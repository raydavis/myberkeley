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

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.notifications.CalendarNotification;
import edu.berkeley.myberkeley.notifications.MessageNotification;
import edu.berkeley.myberkeley.notifications.Notification;
import edu.berkeley.myberkeley.notifications.NotificationTests;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.jcr.RepositoryException;

public class ReceiptEmailerTest extends NotificationTests {

  private ReceiptEmailer receiptEmailer;

  @Mock
  private Session adminSession;

  @Mock
  private ContentManager contentManager;

  @Mock
  AuthorizableManager authMgr;

  @Mock
  private ProfileService profileService;

  @Mock
  private javax.jcr.Session jcrSession;


  public ReceiptEmailerTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() throws IOException, JSONException, CalDavException, RepositoryException, StorageClientException {
    this.receiptEmailer = new ReceiptEmailer();
    this.receiptEmailer.repository = mock(Repository.class);
    this.receiptEmailer.slingRepository = mock(SlingRepository.class);
    this.receiptEmailer.emailSender = new EmailSender();
    this.receiptEmailer.emailSender.profileService = this.profileService;
    this.receiptEmailer.emailSender.smtpServer = "localhost";
    this.receiptEmailer.emailSender.smtpPort = 25;
    this.receiptEmailer.emailSender.sendEmail = false;
    when(this.receiptEmailer.slingRepository.loginAdministrative(null)).thenReturn(this.jcrSession);
    when(this.adminSession.getAuthorizableManager()).thenReturn(this.authMgr);
  }

  @Test
  public void sendCalendarNotification() throws StorageClientException, AccessDeniedException, IOException, JSONException, CalDavException, RepositoryException {
    when(this.receiptEmailer.repository.loginAdministrative()).thenReturn(this.adminSession);
    when(this.adminSession.getContentManager()).thenReturn(this.contentManager);

    Authorizable senderAuthz = mock(Authorizable.class);
    ValueMap senderProfileMap = buildProfileMap("chris@media.berkeley.edu");
    when(this.authMgr.findAuthorizable("904715")).thenReturn(senderAuthz);
    when(this.profileService.getProfileMap(senderAuthz, this.jcrSession)).thenReturn(senderProfileMap);

    List<String> recipients = Arrays.asList("904715");

    Notification notification = new CalendarNotification(new JSONObject(readCalendarNotificationFromFile()));
    this.receiptEmailer.send(notification, recipients);

  }


  @Test
  public void sendMessageNotification() throws StorageClientException, AccessDeniedException, IOException, JSONException, CalDavException, RepositoryException {
    when(this.receiptEmailer.repository.loginAdministrative()).thenReturn(this.adminSession);
    when(this.adminSession.getContentManager()).thenReturn(this.contentManager);

    Authorizable senderAuthz = mock(Authorizable.class);
    ValueMap senderProfileMap = buildProfileMap("chris@media.berkeley.edu");
    when(this.authMgr.findAuthorizable("904715")).thenReturn(senderAuthz);
    when(this.profileService.getProfileMap(senderAuthz, this.jcrSession)).thenReturn(senderProfileMap);

    List<String> recipients = Arrays.asList("904715");

    Notification notification = new MessageNotification(new JSONObject(readMessageNotificationFromFile()));
    this.receiptEmailer.send(notification, recipients);

  }

}
