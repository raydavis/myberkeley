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
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.content.Content;

import java.io.IOException;

public class CalendarNotificationTest extends NotificationTests {

  @Test
  public void fromJSON() throws IOException, JSONException, CalDavException {
    String json = readCalendarNotificationFromFile();
    CalendarNotification notification = (CalendarNotification) NotificationFactory.getFromJSON(new JSONObject(json));
    assertEquals(Notification.SEND_STATE.pending, notification.getSendState());
    assertEquals(Notification.MESSAGEBOX.queue, notification.getMessageBox());
    assertNotNull(notification.getSenderID());
    assertNotNull(notification.getUXState());
    assertNotNull(notification.getUXState().get("eventHour"));
    assertNotNull(notification.getWrapper());
  }

  @Test
  public void toContent() throws IOException, JSONException, CalDavException {
    String json = readCalendarNotificationFromFile();
    CalendarNotification notification = (CalendarNotification) NotificationFactory.getFromJSON(new JSONObject(json));
    Content content = new Content("/some/path", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/some", content);
    assertEquals(content.getProperty(Notification.JSON_PROPERTIES.sendState.toString()), Notification.SEND_STATE.pending.toString());
    assertEquals(content.getProperty(Notification.JSON_PROPERTIES.messageBox.toString()), Notification.MESSAGEBOX.queue.toString());
    assertNotNull(content.getProperty(Notification.JSON_PROPERTIES.uxState.toString()));
    CalendarWrapper wrapper = new CalendarWrapper(new JSONObject((String) content.getProperty(CalendarNotification.JSON_PROPERTIES.calendarWrapper.toString())));
    assertNotNull(wrapper);
    assertTrue(wrapper.isRequired());
  }

  @Test
  public void fromJSONToContentAndBackAgain() throws IOException, JSONException, CalDavException, InterruptedException {
    JSONObject originalJSON = new JSONObject(readCalendarNotificationFromFile());
    CalendarNotification notification = (CalendarNotification) NotificationFactory.getFromJSON(originalJSON);
    Content content = new Content("/some/path", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/some", content);

    // ical4j sets the DTSTAMP of new Calendar instances to the datetime of the instance's creation.
    // when deserializing from content we replace that default DTSTAMP with what's in our data. So here
    // we sleep 1s to test that functionality.
    Thread.sleep(1000);

    Notification notificationFromContent = NotificationFactory.getFromContent(content);
    assertEquals(notification, notificationFromContent);
  }
}
