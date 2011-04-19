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
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import java.util.ArrayList;
import java.util.List;

public class RecipientLog {

  public static final String RESOURCETYPE = "myberkeley/notificationrecipientlog";

  public static final String STORE_NAME = "_myberkeley/recipientlog";

  public static final String PROP_RECIPIENT_TO_CALENDAR_URI = "recipientToCalendarURI";

  Content content;

  private JSONObject recipientToCalendarURIMap;

  public RecipientLog(String path, Session session)
          throws AccessDeniedException, StorageClientException {
    String storePath = StorageClientUtils.newPath(path, STORE_NAME);
    if (!session.getContentManager().exists(storePath)) {
      session.getContentManager().update(new Content(storePath, ImmutableMap.of(
              JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
              (Object) RESOURCETYPE)));
      List<AclModification> modifications = new ArrayList<AclModification>();
      AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
      AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
      AccessControlManager accessControlManager = session.getAccessControlManager();
      accessControlManager.setAcl(Security.ZONE_CONTENT, storePath, modifications.toArray(new AclModification[modifications.size()]));
    }
    this.content = session.getContentManager().get(storePath);

    if (this.content.hasProperty(PROP_RECIPIENT_TO_CALENDAR_URI)) {
      try {
        this.recipientToCalendarURIMap = new JSONObject((String) this.content.getProperty(PROP_RECIPIENT_TO_CALENDAR_URI));
      } catch (JSONException ignored) {
      }
    } else {
      this.recipientToCalendarURIMap = new JSONObject();
    }
  }

  public JSONObject getRecipientToCalendarURIMap() {
    return this.recipientToCalendarURIMap;
  }

  public void update(ContentManager contentManager) throws AccessDeniedException, StorageClientException {
    this.content.setProperty(PROP_RECIPIENT_TO_CALENDAR_URI, this.recipientToCalendarURIMap.toString());
    contentManager.update(this.content);
  }

}
