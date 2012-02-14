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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import edu.berkeley.myberkeley.api.provision.ProvisionResult;
import edu.berkeley.myberkeley.api.provision.SynchronizationState;

import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
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
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class CalOaeAuthorizableServiceTest {
  @Mock
  private DynamicListService dynamicListService;
  @Mock
  private LiteAuthorizablePostProcessService authorizablePostProcessService;
  @Mock
  private ProfileService profileService;
  private CalOaeAuthorizableService calOaeAuthorizableService;

  @Before
  public void setup() throws StorageClientException, AccessDeniedException, ClassNotFoundException, IOException {
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    Repository repository = baseMemoryRepository.getRepository();
    calOaeAuthorizableService = new CalOaeAuthorizableService();
    calOaeAuthorizableService.repository = repository;
    calOaeAuthorizableService.authorizablePostProcessService = authorizablePostProcessService;
    calOaeAuthorizableService.dynamicListService = dynamicListService;
    calOaeAuthorizableService.profileService = profileService;
  }

  @Test
  public void testProfileSetup() throws Exception {
    final String userId = "joe";
    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put("firstName", "Joe")
        .put("lastName", "Student")
        .put("email", "joe@example.edu")
        .put("college", "L & S")
        .put("role", "Undergraduate Student")
        .put( "major", "UNDECLARED")
        .build();
    ProvisionResult provisionResult = calOaeAuthorizableService.loadUser(userId, attributes);
    User user = provisionResult.getUser();
    assertNotNull(user);
    assertEquals(SynchronizationState.created, provisionResult.getSynchronizationState());
    // Empty update to initialize settings for new user.
    // Then update with specified property values.
    ArgumentCaptor<JSONObject> argument = ArgumentCaptor.forClass(JSONObject.class);
    verify(profileService, atLeastOnce()).update(any(Session.class), eq(LitePersonalUtils.getProfilePath(userId)), argument.capture(), eq(true), eq(true), eq(false));
    JSONObject jsonObject = argument.getValue();
    assertEquals("Joe", jsonObject.getJSONObject("basic").getJSONObject("elements").getJSONObject("firstName").getString("value"));
    assertEquals("Student", jsonObject.getJSONObject("basic").getJSONObject("elements").getJSONObject("lastName").getString("value"));
    assertEquals("joe@example.edu", jsonObject.getJSONObject("email").getJSONObject("elements").getJSONObject("email").getString("value"));
    assertEquals("L & S", jsonObject.getJSONObject("institutional").getJSONObject("elements").getJSONObject("college").getString("value"));
    assertEquals("Undergraduate Student", jsonObject.getJSONObject("institutional").getJSONObject("elements").getJSONObject("role").getString("value"));
    assertEquals("UNDECLARED", jsonObject.getJSONObject("institutional").getJSONObject("elements").getJSONObject("major").getString("value"));
  }

  @Test
  public void testDemographics() throws Exception {
    final String userId = "joe";
    final Set<String> demographics = ImmutableSet.of(
        "/colleges/L & S/standings/undergrad/majors/UNDECLARED",
        "/colleges/L & S/standings/undergrad",
        "/student/educ_level/Junior"
    );
    Map<String, Object> attributes = ImmutableMap.of(
        "myb-demographics", (Object) demographics
    );
    ProvisionResult provisionResult = calOaeAuthorizableService.loadUser(userId, attributes);
    User user = provisionResult.getUser();
    assertNotNull(user);
    verify(dynamicListService).setDemographics(any(Session.class), eq(userId), eq(demographics));
  }

  @Test
  public void htmlEntities() throws Exception {
    String firstNameUtf8 = "Français &tc.";
    String lastNameUtf8 = "à Côté";
    final String userId = "fancy";
    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put("firstName", "Fran&ccedil;ais &tc.")
        .put("lastName", "&agrave; C&ocirc;t&eacute;")
        .put("email", "fancy@example.edu")
        .build();
    ProvisionResult provisionResult = calOaeAuthorizableService.loadUser(userId, attributes);
    User user = provisionResult.getUser();
    assertNotNull(user);
    ArgumentCaptor<JSONObject> argument = ArgumentCaptor.forClass(JSONObject.class);
    verify(profileService, atLeastOnce()).update(any(Session.class), eq(LitePersonalUtils.getProfilePath(userId)), argument.capture(), eq(true), eq(true), eq(false));
    JSONObject jsonObject = argument.getValue();
    assertEquals(firstNameUtf8, jsonObject.getJSONObject("basic").getJSONObject("elements").getJSONObject("firstName").getString("value"));
    assertEquals(lastNameUtf8, jsonObject.getJSONObject("basic").getJSONObject("elements").getJSONObject("lastName").getString("value"));
  }

}
