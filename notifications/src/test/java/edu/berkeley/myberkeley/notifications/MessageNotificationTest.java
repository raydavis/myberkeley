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
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import net.fortuna.ical4j.model.Date;
import org.apache.commons.httpclient.URI;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.content.Content;

import java.io.IOException;

public class MessageNotificationTest extends NotificationTests {

  @Test
  public void fromJSON() throws IOException, JSONException, CalDavException {
    String json = readMessageNotificationFromFile();
    MessageNotification notification = (MessageNotification) NotificationFactory.getFromJSON(new JSONObject(json));
    assertEquals(Notification.SEND_STATE.pending, notification.getSendState());
    assertEquals(Notification.MESSAGEBOX.queue, notification.getMessageBox());
    assertNotNull(notification.getSenderID());
    assertNotNull(notification.getBody());
    assertNotNull(notification.getSubject());
  }

  @Test
  public void toContent() throws IOException, JSONException, CalDavException {
    String json = readMessageNotificationFromFile();
    MessageNotification notification = (MessageNotification) NotificationFactory.getFromJSON(new JSONObject(json));
    Content content = new Content("/some/path", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/some", content);
    assertEquals(content.getProperty(Notification.JSON_PROPERTIES.sendState.toString()), Notification.SEND_STATE.pending.toString());
    assertEquals(content.getProperty(Notification.JSON_PROPERTIES.messageBox.toString()), Notification.MESSAGEBOX.queue.toString());
    assertEquals(notification.getBody(), content.getProperty(MessageNotification.JSON_PROPERTIES.body.toString()));
    assertEquals(notification.getSubject(), content.getProperty(MessageNotification.JSON_PROPERTIES.subject.toString()));

  }

  @Test
  public void fromJSONToContentAndBackAgain() throws IOException, JSONException, CalDavException {
    JSONObject originalJSON = new JSONObject(readMessageNotificationFromFile());
    JSONObject recipMap = new JSONObject();
    recipMap.put("904715", new CalendarURI(new URI("foo", false), new Date()).toJSON());
    Notification notification = NotificationFactory.getFromJSON(originalJSON);
    Content content = new Content("/some/path", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/some", content);
    Notification notificationFromContent = NotificationFactory.getFromContent(content);
    assertEquals(notification, notificationFromContent);

  }
}
