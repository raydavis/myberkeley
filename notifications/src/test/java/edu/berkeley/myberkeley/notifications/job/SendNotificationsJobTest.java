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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import edu.berkeley.myberkeley.caldav.CalDavConnectorProviderImpl;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavConnectorProvider;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.notifications.Notification;
import edu.berkeley.myberkeley.notifications.NotificationTests;
import edu.berkeley.myberkeley.notifications.RecipientLog;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import org.apache.commons.httpclient.URI;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.jcr.RepositoryException;

public class SendNotificationsJobTest extends NotificationTests {

  private static final String RECIPIENT_ID = "904715"; 
  
  private SendNotificationsJob job;

  @Mock
  private JobContext context;

  @Mock
  private Session adminSession;

  @Mock
  private AccessControlManager accessControlManager;

  @Mock
  private ContentManager cm;

  public SendNotificationsJobTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() throws StorageClientException, AccessDeniedException, IOException, RepositoryException {
    Repository repo = mock(Repository.class);
    CalDavConnectorProvider provider = mock(CalDavConnectorProviderImpl.class);
    NotificationEmailSender emailSender = mock(NotificationEmailSender.class);
    DynamicListService dynamicListService = mock(DynamicListService.class);
    SlingRepository slingRepository = mock(SlingRepository.class);

    this.job = new SendNotificationsJob(repo, slingRepository, emailSender, provider, dynamicListService);

    Content dynamicList = new Content("/a/path/to/a/dynamic/list", ImmutableMap.of(
      JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    dynamicList.setProperty("context", "g-ced-students");
    dynamicList.setProperty("filter", "mock filter");
    when(this.cm.get("/a/path/to/a/dynamic/list")).thenReturn(dynamicList);

    when(this.job.sparseRepository.loginAdministrative()).thenReturn(this.adminSession);
    when(this.adminSession.getContentManager()).thenReturn(this.cm);
    when(this.adminSession.getAccessControlManager()).thenReturn(this.accessControlManager);

    when(this.job.dynamicListService.getUserIdsForNode(Matchers.<Content>any(), Matchers.<Session>any())).thenReturn(
            Arrays.asList(RECIPIENT_ID));
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

    CalDavConnector connector = mock(CalDavConnector.class);
    when(this.job.calDavConnectorProvider.getAdminConnector(RECIPIENT_ID)).thenReturn(connector);
    CalendarURI uri = new CalendarURI(new URI("/some/bedework/address", false), new Date());
    when(connector.putCalendar(Matchers.<Calendar>any())).thenReturn(uri);
    when(this.job.emailSender.send(Matchers.<Notification>any(), Matchers.<List<String>>any())).thenReturn("12345");

    when(this.cm.exists("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME)).thenReturn(false);
    Content logContent = mock(Content.class);
    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME)).thenReturn(logContent);

    this.job.execute(this.context);

    verify(connector).putCalendar(Matchers.<Calendar>any());
    verify(this.cm).update(content);
    verify(this.cm).update(logContent);
    verify(this.adminSession).logout();
    verify(this.job.emailSender).send(Matchers.<Notification>any(), Matchers.<List<String>>any());
  }

  @Test
  public void executeWhenEmailHasAlreadyBeenSent() throws StorageClientException, AccessDeniedException, IOException,
          JSONException, CalDavException {

    JSONObject json = new JSONObject(readNotificationFromFile());
    Notification notification = new Notification(json);

    Content content = new Content("a:123456/_myberkeley_notificationstore/notice1", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/notice1", content);
    List<Content> results = new ArrayList<Content>();
    results.add(content);
    when(this.cm.find(Matchers.<Map<String, Object>>any())).thenReturn(results);

    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1")).thenReturn(content);

    CalDavConnector connector = mock(CalDavConnector.class);
    when(this.job.calDavConnectorProvider.getAdminConnector(RECIPIENT_ID)).thenReturn(connector);
    CalendarURI uri = new CalendarURI(new URI("/some/bedework/address", false), new Date());
    when(connector.putCalendar(Matchers.<Calendar>any())).thenReturn(uri);

    when(this.cm.exists("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME)).thenReturn(false);
    Content logContent = new Content("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME,
            ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                    (Object) RecipientLog.RESOURCETYPE));
    logContent.setProperty(RecipientLog.PROP_EMAIL_MESSAGE_ID, "messageID12345");
    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME)).thenReturn(logContent);

    this.job.execute(this.context);

    verify(connector).putCalendar(Matchers.<Calendar>any());
    verify(this.cm).update(content);
    verify(this.cm).update(logContent);
    verify(this.adminSession).logout();
    verify(this.job.emailSender, times(0)).send(Matchers.<Notification>any(), Matchers.<List<String>>any());
  }

  @Test
  public void executeWhenCalendarHasAlreadyBeenSent() throws StorageClientException, AccessDeniedException, IOException,
          JSONException, CalDavException {

    JSONObject json = new JSONObject(readNotificationFromFile());
    Notification notification = new Notification(json);

    Content content = new Content("a:123456/_myberkeley_notificationstore/notice1", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) Notification.RESOURCETYPE));
    notification.toContent("/notice1", content);
    List<Content> results = new ArrayList<Content>();
    results.add(content);
    when(this.cm.find(Matchers.<Map<String, Object>>any())).thenReturn(results);

    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1")).thenReturn(content);

    CalDavConnector connector = mock(CalDavConnector.class);
    when(this.job.calDavConnectorProvider.getAdminConnector(RECIPIENT_ID)).thenReturn(connector);
    CalendarURI uri = new CalendarURI(new URI("/some/bedework/address", false), new Date());
    when(connector.putCalendar(Matchers.<Calendar>any())).thenReturn(uri);

    when(this.cm.exists("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME)).thenReturn(true);
    Content logContent = new Content("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME,
            ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                    (Object) RecipientLog.RESOURCETYPE));
    JSONObject recipMap = new JSONObject();
    recipMap.put(RECIPIENT_ID, new CalendarURI(new URI("foo", false), new Date()).toJSON());
    logContent.setProperty(RecipientLog.PROP_RECIPIENT_TO_CALENDAR_URI, recipMap.toString());
    when(this.cm.get("a:123456/_myberkeley_notificationstore/notice1/" + RecipientLog.STORE_NAME)).thenReturn(logContent);

    this.job.execute(this.context);

    verify(connector, times(0)).putCalendar(Matchers.<Calendar>any());
    verify(this.cm).update(content);
    verify(this.cm).update(logContent);
    verify(this.adminSession).logout();
    verify(this.job.emailSender, times(1)).send(Matchers.<Notification>any(), Matchers.<List<String>>any());
  }

}
