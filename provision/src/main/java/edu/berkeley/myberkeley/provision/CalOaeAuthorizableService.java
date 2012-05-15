/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package edu.berkeley.myberkeley.provision;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import org.sakaiproject.nakamura.api.accountprovider.OaeAuthorizableService;
import org.sakaiproject.nakamura.api.accountprovider.ProvisionResult;
import org.sakaiproject.nakamura.api.accountprovider.SynchronizationState;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.servlets.post.ModificationType;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static edu.berkeley.myberkeley.api.dynamiclist.DynamicListService.DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP;

@Component(metatype = true,
label = "CalCentral :: OAE User Service", description = "Create and update valid CalCentral user accounts in a single request")
@Service
public class CalOaeAuthorizableService implements OaeAuthorizableService {
  private static final Logger LOGGER = LoggerFactory.getLogger(CalOaeAuthorizableService.class);
  public static final SimpleDateFormat DEFAULT_JAVASCRIPT_DATE_FORMAT = new SimpleDateFormat("EEE MMM d yyyy HH:mm:mm 'GMT'Z (z)");
  public static final Set<String> BASIC_PROFILE_PROPERTY_NAMES = ImmutableSet.copyOf(new String[] {
      "firstName",
      "lastName",
      "locale",
      "timezone"
  });
  public static final Set<String> INSTITUTIONAL_PROFILE_PROPS = ImmutableSet.copyOf(new String[] {
      "college",
      "major",
      "role"
  });
  public static final Set<String> EMAIL_PROFILE_PROPS = ImmutableSet.copyOf(new String[] {
      "email"
  });
  public static final Map<String, Set<String>> PROFILE_SECTIONS = ImmutableMap.of(
      "basic", BASIC_PROFILE_PROPERTY_NAMES,
      "institutional", INSTITUTIONAL_PROFILE_PROPS,
      "email", EMAIL_PROFILE_PROPS
  );
  public static final Map<String, Object[]> USER_PROPERTY_DEFAULTS = ImmutableMap.of(
      "locale", new Object[] {"en_US"},
      "timezone", new Object[] {"America/Los_Angeles"}
  );
  public static final String INITIAL_PROFILE_CONTENT =
      "{'institutional':{'anonymous@deny':{'operation':'replace', 'permission':['all']}}," +
      "'email':{'anonymous@deny':{'operation':'replace', 'permission':['all']}," +
      "'everyone@deny':{'operation':'replace', 'permission':['all']}," +
      "'%USER_ID%@grant':{'operation':'replace', 'permission':['all']}}}";
  public static final String PARTICIPANT_PROFILE_CONTENT =
      "{'myberkeley':{'elements':{'joinDate':{'date':'%JOIN_DATE%'}," +
      "'participant':{'value':'true'}}}}";
  /**
   * Property to explicitly set the user's Sparse account password.
   * This should only be used when loading test users, not in a production
   * or QA deployment where external authentication is used.
   */
  public static final String PASSWORD_PROPERTY_NAME = "pwd";

  @Reference
  DynamicListService dynamicListService;
  @Reference
  LiteAuthorizablePostProcessService authorizablePostProcessService;
  @Reference
  ProfileService profileService;
  @Reference
  Repository repository;

  @Override
  public ProvisionResult loadUser(String userId, Map<String, Object> attributes) {
    User user = null;
    Session adminSession = null;
    SynchronizationState synchronizationState = SynchronizationState.error;
    try {
      adminSession = repository.loginAdministrative();
      final AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
      Authorizable authorizable = authorizableManager.findAuthorizable(userId);
      if (authorizable == null) {
        user = createOaeUser(userId, adminSession);
        synchronizationState = SynchronizationState.created;
      } else if (authorizable instanceof User) {
        user = (User) authorizable;
        synchronizationState = SynchronizationState.refreshed;
      } else {
        LOGGER.warn("Specified user ID {} resolves to {}", userId, authorizable);
      }
      if (user != null) {
        loadCalAttributes(user, attributes, adminSession);
        user = (User) authorizableManager.findAuthorizable(userId);
      }
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    }
    return new ProvisionResult(user, synchronizationState);
  }

  @Override
  public void initializeParticipant(String userId) {
    Session session = null;
    try {
      session = repository.loginAdministrative();
      // Stay compatible with our opt-in JavaScript, which has been storing the
      // join date as String rather than Date or Calendar.
      final String joinDate = DEFAULT_JAVASCRIPT_DATE_FORMAT.format(new Date());
      final String jsonContentString = PARTICIPANT_PROFILE_CONTENT.replaceAll("%JOIN_DATE%", joinDate);
      JSONObject jsonContent = new JSONObject(jsonContentString);
      profileService.update(session, LitePersonalUtils.getProfilePath(userId), jsonContent, true, true, false);
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      if (session != null) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void loadCalAttributes(User user, Map<String, Object> attributes, Session session)
      throws JSONException, StorageClientException, AccessDeniedException {
    // Check for explicit password change.
    final String password = (String) attributes.get(PASSWORD_PROPERTY_NAME);
    if (password != null) {
      final AuthorizableManager authorizableManager = session.getAuthorizableManager();
      authorizableManager.changePassword(user, password, "");
    }

    // Build JSON object corresponding to profile properties.
    final String userId = user.getId();
    JSONObject importJson = new JSONObject();
    for (String sectionName : PROFILE_SECTIONS.keySet()) {
      final Set<String> profileProps = Sets.intersection(PROFILE_SECTIONS.get(sectionName), attributes.keySet());
      if (profileProps.size() > 0) {
        JSONObject elementsJson = new JSONObject();
        for (String key : profileProps) {
          JSONObject itemJson = new JSONObject();
          
          // Some clever people have managed to inject HTML entities (rather than UTF-8
          // strings) into campus data sources. For functional compatibility with
          // bSpace and CalNet directory displays, try to decode them.
          final String value = StringEscapeUtils.unescapeHtml((String) attributes.get(key));
          
          itemJson.put("value", value);
          elementsJson.put(key, itemJson);
        }
        JSONObject sectionJson = new JSONObject();
        sectionJson.put("elements", elementsJson);
        importJson.put(sectionName, sectionJson);
      }
    }

    // Update profile.
    profileService.update(session, LitePersonalUtils.getProfilePath(userId), importJson, true, true, false);

    // Update demographic data.
    if (attributes.containsKey(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP)) {
      // NEED TO HANDLE SET TRANSLATION HERE INSTEAD
      dynamicListService.setDemographics(session, userId, (Set<String>) attributes.get(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP));
    }
  }

  private User createOaeUser(String userId, Session session)
      throws AccessDeniedException, StorageClientException, JSONException {
    // Create a bare-bones OAE User record.
    final String password = RandomStringUtils.random(16);
    final AuthorizableManager authorizableManager = session.getAuthorizableManager();
    if (authorizableManager.createUser(userId, userId, password, null)) {
      LOGGER.info("Created OAE user {}", userId);
    } else {
      LOGGER.warn("Did not create OAE user {}", userId);
      return null;
    }
    User user = (User) authorizableManager.findAuthorizable(userId);
    Map<String, Object[]> postProcessProperties = Maps.newHashMap(USER_PROPERTY_DEFAULTS);
    try {
      authorizablePostProcessService.process(user, session, ModificationType.CREATE, postProcessProperties);
    } catch (Exception e) {
      LOGGER.error("Unknown exception in post-processing for user " + userId, e);
    }

    // Initialize profile data permissions.
    final String jsonContentString = INITIAL_PROFILE_CONTENT.replaceAll("%USER_ID%", userId);
    JSONObject jsonContent = new JSONObject(jsonContentString);
    profileService.update(session, LitePersonalUtils.getProfilePath(userId), jsonContent, true, true, false);

    return user;
  }
}
