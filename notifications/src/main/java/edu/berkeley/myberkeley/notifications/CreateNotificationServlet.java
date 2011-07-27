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

package edu.berkeley.myberkeley.notifications;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(selectors = {"myb-notificationstore"}, methods = {"POST"}, resourceTypes = {"sakai/user-home"},
        generateService = true, generateComponent = true)
@Properties(value = {
        @Property(name = "service.vendor", value = "MyBerkeley"),
        @Property(name = "service.description", value = "Endpoint to create a notification")})
public class CreateNotificationServlet extends SlingAllMethodsServlet {

  private static final long serialVersionUID = -1868784233373889299L;

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateNotificationServlet.class);

  enum POST_PARAMS {
    notification
  }

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    RequestParameter notificationParam = request.getRequestParameter(POST_PARAMS.notification.toString());
    JSONObject notificationJSON;

    if (notificationParam == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
              "The " + POST_PARAMS.notification + " parameter must not be null");
      return;
    }

    try {
      notificationJSON = new JSONObject(notificationParam.toString());
      LOGGER.debug("Notification JSON = " + notificationJSON.toString(2));

    } catch (JSONException je) {
      LOGGER.error("Failed to convert notification to JSON", je);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
              "Got a JSONException parsing input");
      return;
    }

    Resource r = request.getResource();
    Content home = r.adaptTo(Content.class);

    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
            javax.jcr.Session.class));

    try {
      ContentManager contentManager = session.getContentManager();
      String storePath = StorageClientUtils.newPath(home.getPath(), Notification.STORE_NAME);
      createStoreIfNecessary(session, contentManager, storePath);

      Notification.MESSAGEBOX box = Notification.MESSAGEBOX.valueOf(notificationJSON.getString(Notification.JSON_PROPERTIES.messageBox.toString()));
      if (Notification.MESSAGEBOX.drafts.equals(box) || Notification.MESSAGEBOX.trash.equals(box)) {
        // it's a draft or trash, save it as raw JSON
        UUID id = Notification.getNotificationID(notificationJSON);
        String draftPath = StorageClientUtils.newPath(storePath, id.toString());
        Content draftContent = createNotificationIfNecessary(contentManager, draftPath);
        Iterator<String> keys = notificationJSON.keys();
        while (keys.hasNext()) {
          String key = keys.next();
          Object val = notificationJSON.get(key);
          if (val != null) {
            try {
              ISO8601Date date = new ISO8601Date(val.toString());
              draftContent.setProperty(key, date.toString());
            } catch (IllegalArgumentException ignored) {
              draftContent.setProperty(key, val.toString());
            }
          }
        }
        draftContent.setProperty(Notification.JSON_PROPERTIES.id.toString(), id.toString());
        draftContent.setProperty("sakai:messagestore", storePath);
        contentManager.update(draftContent);
      } else {
        // it's not a draft, save it as a real Notification
        Notification notification = NotificationFactory.getFromJSON(notificationJSON);
        String notificationPath = StorageClientUtils.newPath(storePath, notification.getId().toString());
        Content notificationContent = createNotificationIfNecessary(contentManager, notificationPath);
        notification.toContent(storePath, notificationContent);
        contentManager.update(notificationContent);
        LOGGER.debug("Saved a Notification;  data = {}", notificationContent);
      }


    } catch (StorageClientException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException ade) {
      throw new ServletException(ade.getMessage(), ade);
    } catch (JSONException je) {
      throw new ServletException(je.getMessage(), je);
    } catch (CalDavException cde) {
      throw new ServletException(cde.getMessage(), cde);
    }
  }

  private Content createNotificationIfNecessary(ContentManager contentManager, String notificationPath) throws AccessDeniedException, StorageClientException {
    if (!contentManager.exists(notificationPath)) {
      LOGGER.debug("Creating new notification at path " + notificationPath);
      contentManager.update(new Content(notificationPath, ImmutableMap.of(
              JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
              (Object) Notification.RESOURCETYPE)));
    }
    return contentManager.get(notificationPath);
  }

  private Content createStoreIfNecessary(Session session, ContentManager contentManager, String storePath) throws AccessDeniedException, StorageClientException {
    if (!contentManager.exists(storePath)) {
      LOGGER.debug("Will create a new notification store for user at path " + storePath);
      contentManager.update(new Content(storePath, ImmutableMap.of(
              JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
              (Object) Notification.STORE_RESOURCETYPE)));
      List<AclModification> modifications = new ArrayList<AclModification>();
      AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
      AccessControlManager accessControlManager = session.getAccessControlManager();
      accessControlManager.setAcl(Security.ZONE_CONTENT, storePath, modifications.toArray(new AclModification[modifications.size()]));
    }
    return contentManager.get(storePath);
  }

}
