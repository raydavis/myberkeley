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
package edu.berkeley.myberkeley.dynamiclist;

import com.google.common.collect.ImmutableMap;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * After a user account has been loaded into MyBerkeley, it must be
 * maintained. If the account remains among the standard target population,
 * this will happen every time the target population data is refreshed.
 * However, if the user has dropped out of the target population, the
 * obsolete account data would be missed. The data synchronization script
 * therefore needs a way to get "all user IDs for accounts which were
 * loaded from campus systems." This servlet provides that functionality,
 * based on the personal demographics record loaded from Oracle
 * materialized views.
 *
 * This servlet must be protected against all non-administrative
 * sessions.
 */
@SlingServlet(extensions = {"json"}, methods = {"GET"},
    paths = {"/system/myberkeley/userIds"},
    generateComponent = true, generateService = true)
public class GetUserIdsServlet extends SlingSafeMethodsServlet {
  private static final long serialVersionUID = 7368025436453141350L;
  private static final Logger LOGGER = LoggerFactory.getLogger(GetUserIdsServlet.class);

  private static final Map<String, Object> SPARSE_QUERY_MAP = ImmutableMap.of(
      JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
      (Object) UserConstants.USER_HOME_RESOURCE_TYPE,
      "_items", new Long(20000)
  );

  @Reference
  protected transient DynamicListService dynamicListService;

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
          throws ServletException, IOException {
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
            javax.jcr.Session.class));
    if (!isAdminUser(session)) {
      LOGGER.error("GetUserIdsServlet called by " + session.getUserId());
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    List<String> nonparticipants = new ArrayList<String>();
    List<String> participants = new ArrayList<String>();
    List<String> dropped = new ArrayList<String>();
    try {
      ContentManager contentManager = session.getContentManager();
      Iterable<Content> homeNodes = contentManager.find(SPARSE_QUERY_MAP);
      for (Content homeNode : homeNodes) {
        String path = homeNode.getPath();
        String userId = PathUtils.getAuthorizableId(path);
        String participantStatusPath = path + "/public/authprofile/myberkeley/elements/participant";
        String demographicPath = path + "/_myberkeley-demographic";
        boolean isParticipant = contentManager.exists(participantStatusPath);
        Content demographicNode = contentManager.get(demographicPath);
        boolean isIntegrated = (demographicNode != null) &&
            demographicNode.hasProperty(DynamicListService.DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP);
        if (isParticipant && !isIntegrated) {
          Content emailNode = contentManager.get(path + "/public/authprofile/email/elements/email");
          boolean wasDropped = (emailNode == null) || (!emailNode.hasProperty("value"));
          if (wasDropped) {
            LOGGER.warn("Participant {} was mistakenly dropped", userId);
          }
        }
        if (isParticipant) {
          participants.add(userId);
        } else if (isIntegrated) {
          nonparticipants.add(userId);
        } else {
          dropped.add(userId);
        }
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
    try {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      JSONWriter write = new JSONWriter(response.getWriter());
      write.object();
      write.key("dropped").array();
      for (String userId : dropped) {
        write.value(userId);
      }
      write.endArray();
      write.key("nonparticipants").array();
      for (String userId : nonparticipants) {
        write.value(userId);
      }
      write.endArray();
      write.key("participants").array();
      for (String userId : participants) {
        write.value(userId);
      }
      write.endArray();
      write.endObject();
    } catch (JSONException e) {
      LOGGER.warn(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create the proper JSON structure.");
    }
  }

  private static boolean isAdminUser(Session session) {
    try {
      User currentUser = (User) session.getAuthorizableManager().findAuthorizable(session.getUserId());
      return currentUser.isAdmin();
    } catch (AccessDeniedException e) {
      return false;
    } catch (StorageClientException e) {
      return false;
    }
  }

}
