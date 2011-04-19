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

package edu.berkeley.myberkeley.notifications.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListContext;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import edu.berkeley.myberkeley.caldav.CalDavConnectorImpl;
import edu.berkeley.myberkeley.caldav.CalDavConnectorProviderImpl;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavConnectorProvider;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.notifications.Notification;
import edu.berkeley.myberkeley.notifications.NotificationTests;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import org.apache.commons.httpclient.URI;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SendNotificationsJobTest extends NotificationTests {

  private SendNotificationsJob job;

  @Mock
  private JobContext context;

  @Mock
  private Session adminSession;

  @Mock
  private ContentManager cm;

  public SendNotificationsJobTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() throws StorageClientException, AccessDeniedException, IOException {
    Repository repo = mock(Repository.class);
    CalDavConnectorProvider provider = mock(CalDavConnectorProviderImpl.class);
    NotificationEmailSender emailSender = mock(NotificationEmailSender.class);
    DynamicListService dynamicListService = mock(DynamicListService.class);

    this.job = new SendNotificationsJob(repo, emailSender, provider, dynamicListService);

    when(this.job.repository.loginAdministrative()).thenReturn(this.adminSession);
    when(this.adminSession.getContentManager()).thenReturn(this.cm);
    when(this.job.dynamicListService.getUserIdsForCriteria(Matchers.<DynamicListContext>any(), Matchers.anyString())).thenReturn(
            Arrays.asList("300847"));
  }

  @Test
  public void execute() throws StorageClientException, AccessDeniedException, IOException, JSONException, CalDavException {

    Notification notification = new Notification(new JSONObject(readNotificationFromFile()));
    Content content = new Content("a:123456/_myberkeley_notificationstore/notice1", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/notice1", content);
    List<Content> results = new ArrayList<Content>();
    results.add(content);
    when(this.cm.find(Matchers.<Map<String, Object>>any())).thenReturn(results);

    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1")).thenReturn(content);

    CalDavConnector connector = mock(CalDavConnectorImpl.class);
    when(this.job.calDavConnectorProvider.getAdminConnector()).thenReturn(connector);
    CalendarURI uri = new CalendarURI(new URI("/some/bedework/address", false), new Date());
    when(connector.putCalendar(Matchers.<Calendar>any(), Matchers.anyString())).thenReturn(uri);
    when(this.job.emailSender.send(Matchers.<Notification>any(), Matchers.<List<String>>any())).thenReturn("12345");
    this.job.execute(this.context);

    verify(connector).putCalendar(Matchers.<Calendar>any(), Matchers.anyString());
    verify(this.cm).update(Matchers.<Content>any());
    verify(this.adminSession).logout();
    verify(this.job.emailSender).send(Matchers.<Notification>any(), Matchers.<List<String>>any());
  }

  @Test
  public void executeWhenEmailHasAlreadyBeenSent() throws StorageClientException, AccessDeniedException, IOException,
          JSONException, CalDavException {

    JSONObject json = new JSONObject(readNotificationFromFile());
    json.put(Notification.JSON_PROPERTIES.emailMessageID.toString(), "some message id");
    Notification notification = new Notification(json);

    Content content = new Content("a:123456/_myberkeley_notificationstore/notice1", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/notice1", content);
    List<Content> results = new ArrayList<Content>();
    results.add(content);
    when(this.cm.find(Matchers.<Map<String, Object>>any())).thenReturn(results);

    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1")).thenReturn(content);

    CalDavConnector connector = mock(CalDavConnectorImpl.class);
    when(this.job.calDavConnectorProvider.getAdminConnector()).thenReturn(connector);
    CalendarURI uri = new CalendarURI(new URI("/some/bedework/address", false), new Date());
    when(connector.putCalendar(Matchers.<Calendar>any(), Matchers.anyString())).thenReturn(uri);

    this.job.execute(this.context);

    verify(connector).putCalendar(Matchers.<Calendar>any(), Matchers.anyString());
    verify(this.cm).update(Matchers.<Content>any());
    verify(this.adminSession).logout();
    verify(this.job.emailSender, times(0)).send(Matchers.<Notification>any(), Matchers.<List<String>>any());
  }

  @Test
  public void executeWhenCalendarHasAlreadyBeenSent() throws StorageClientException, AccessDeniedException, IOException,
          JSONException, CalDavException {

    JSONObject json = new JSONObject(readNotificationFromFile());
    JSONObject recipMap = new JSONObject();
    recipMap.put("300847", new CalendarURI(new URI("foo", false), new Date()).toJSON());
    json.put(Notification.JSON_PROPERTIES.recipientToCalendarURIMap.toString(), recipMap);
    Notification notification = new Notification(json);

    Content content = new Content("a:123456/_myberkeley_notificationstore/notice1", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/notice1", content);
    List<Content> results = new ArrayList<Content>();
    results.add(content);
    when(this.cm.find(Matchers.<Map<String, Object>>any())).thenReturn(results);

    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1")).thenReturn(content);

    CalDavConnector connector = mock(CalDavConnectorImpl.class);
    when(this.job.calDavConnectorProvider.getAdminConnector()).thenReturn(connector);
    CalendarURI uri = new CalendarURI(new URI("/some/bedework/address", false), new Date());
    when(connector.putCalendar(Matchers.<Calendar>any(), Matchers.anyString())).thenReturn(uri);

    this.job.execute(this.context);

    verify(connector, times(0)).putCalendar(Matchers.<Calendar>any(), Matchers.anyString());
    verify(this.cm).update(Matchers.<Content>any());
    verify(this.adminSession).logout();
    verify(this.job.emailSender, times(1)).send(Matchers.<Notification>any(), Matchers.<List<String>>any());
  }

}
