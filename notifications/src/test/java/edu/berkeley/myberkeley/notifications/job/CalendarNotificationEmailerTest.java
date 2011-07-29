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
import edu.berkeley.myberkeley.notifications.NotificationTests;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.service.component.ComponentContext;
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
import java.util.HashMap;
import java.util.List;
import javax.jcr.RepositoryException;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

public class CalendarNotificationEmailerTest extends NotificationTests {

  private CalendarNotificationEmailer sender;

  private CalendarNotification notification;

  @Mock
  private ComponentContext componentContext;

  @Mock
  private ContentManager contentManager;

  @Mock
  private Session adminSession;

  @Mock
  private javax.jcr.Session jcrSession;

  @Mock
  private ProfileService profileService;

  @Mock
  AuthorizableManager authMgr;

  public CalendarNotificationEmailerTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() throws IOException, JSONException, CalDavException, RepositoryException, StorageClientException {
    this.sender = new CalendarNotificationEmailer();
    this.sender.repository = mock(Repository.class);
    this.sender.slingRepository = mock(SlingRepository.class);
    this.sender.emailSender = new EmailSender();
    this.sender.emailSender.profileService = this.profileService;
    this.notification = new CalendarNotification(new JSONObject(readCalendarNotificationFromFile()));

    this.sender.emailSender.smtpServer = "localhost";
    this.sender.emailSender.smtpPort = 25;
    this.sender.emailSender.sendEmail = false;

    when(this.sender.slingRepository.loginAdministrative(null)).thenReturn(this.jcrSession);
    when(this.adminSession.getAuthorizableManager()).thenReturn(this.authMgr);
  }

  @Test
  public void send() throws StorageClientException, AccessDeniedException, IOException, JSONException, CalDavException, RepositoryException {
    when(this.sender.repository.loginAdministrative()).thenReturn(this.adminSession);
    when(this.adminSession.getContentManager()).thenReturn(this.contentManager);

    Authorizable senderAuthz = mock(Authorizable.class);
    ValueMap senderProfileMap = buildProfileMap("chris@media.berkeley.edu");
    when(this.authMgr.findAuthorizable("904715")).thenReturn(senderAuthz);
    when(this.profileService.getProfileMap(senderAuthz, this.jcrSession)).thenReturn(senderProfileMap);

    List<String> recipients = Arrays.asList("904715");
    this.sender.send(this.notification, recipients);

  }

  @Test(expected = EmailException.class)
  public void buildEmailWithBogusSender() throws EmailException, StorageClientException, AccessDeniedException, MessagingException, RepositoryException {
    List<String> recips = Arrays.asList("user@foo.com");

    Authorizable senderAuthz = mock(Authorizable.class);
    ValueMap senderProfileMap = buildProfileMap("not an email");
    when(this.authMgr.findAuthorizable("904715")).thenReturn(senderAuthz);
    when(this.profileService.getProfileMap(senderAuthz, this.jcrSession)).thenReturn(senderProfileMap);

    this.sender.buildEmail(this.notification, recips, this.adminSession, this.jcrSession);
  }

  @Test
  public void buildEmail() throws EmailException, StorageClientException, AccessDeniedException, MessagingException, RepositoryException {
    List<String> recips = Arrays.asList("joe@media.berkeley.edu", "not.an.email");

    Authorizable senderAuthz = mock(Authorizable.class);
    ValueMap senderProfileMap = buildProfileMap("chris@media.berkeley.edu");
    when(this.authMgr.findAuthorizable("904715")).thenReturn(senderAuthz);
    when(this.profileService.getProfileMap(senderAuthz, this.jcrSession)).thenReturn(senderProfileMap);

    MultiPartEmail email = this.sender.buildEmail(this.notification, recips, this.adminSession, this.jcrSession);
    assertNotNull(email);
    assertEquals("chris@media.berkeley.edu", email.getFromAddress().getAddress());
    boolean senderInBCC = false;
    for (Address address : email.getMimeMessage().getAllRecipients()) {
      if ("chris@media.berkeley.edu".equals(((InternetAddress) address).getAddress())) {
        senderInBCC = true;
        break;
      }
    }
    assertTrue(senderInBCC);
  }

}
