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

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.notifications.CalendarNotification;
import edu.berkeley.myberkeley.notifications.NotificationTests;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
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
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

public class NotificationEmailSenderTest extends NotificationTests {

  private NotificationEmailSender sender;

  private CalendarNotification notification;

  @Mock
  private ComponentContext componentContext;

  @Mock
  private ContentManager contentManager;

  public NotificationEmailSenderTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() throws IOException, JSONException, CalDavException {
    this.sender = new NotificationEmailSender();
    this.sender.repository = mock(Repository.class);
    this.notification = new CalendarNotification(new JSONObject(readCalendarNotificationFromFile()));

    this.sender.smtpServer = "localhost";
    this.sender.smtpPort = 25;
    this.sender.sendEmail = false;
  }

  @Test
  public void activate() throws Exception {
    Dictionary<String, Object> dictionary = new Hashtable<String, Object>();
    dictionary.put(NotificationEmailSender.SEND_EMAIL, true);
    dictionary.put(NotificationEmailSender.SMTP_PORT, 27);
    dictionary.put(NotificationEmailSender.SMTP_SERVER, "anotherhost");
    when(this.componentContext.getProperties()).thenReturn(dictionary);
    this.sender.activate(this.componentContext);
    assertTrue(this.sender.sendEmail);
    assertEquals(this.sender.smtpPort, (Integer) 27);
    assertEquals(this.sender.smtpServer, "anotherhost");
  }

  @Test
  public void deactivate() throws Exception {
    this.sender.deactivate(this.componentContext);
  }

  @Test
  public void send() throws StorageClientException, AccessDeniedException, IOException, JSONException, CalDavException {
    Session adminSession = mock(Session.class);
    when(this.sender.repository.loginAdministrative()).thenReturn(adminSession);
    when(adminSession.getContentManager()).thenReturn(this.contentManager);

    Content firstRecip = new Content("/user1", ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) "user"));
    firstRecip.setProperty("value", "chris@media.berkeley.edu");
    when(this.contentManager.get(LitePersonalUtils.getProfilePath("904715") + NotificationEmailSender.EMAIL_NODE_PATH)).thenReturn(firstRecip);

    Content participant = new Content("/participant1", ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) "user"));
    participant.setProperty("value", "true");
    when(this.contentManager.get(LitePersonalUtils.getProfilePath("904715") + NotificationEmailSender.MYBERKELEY_PARTICIPANT_NODE_PATH)).thenReturn(participant);

    List<String> recipients = Arrays.asList("904715");
    this.sender.send(this.notification, recipients);

  }

  @Test(expected = EmailException.class)
  public void buildEmailWithBogusSender() throws EmailException, StorageClientException, AccessDeniedException, MessagingException {
    List<String> recips = Arrays.asList("user@foo.com");
    Content badSender = new Content("/user1", ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) "user"));
    badSender.setProperty("value", "not an email");
    when(this.contentManager.get(LitePersonalUtils.getProfilePath("904715") + NotificationEmailSender.EMAIL_NODE_PATH)).thenReturn(badSender);
    this.sender.buildEmail(this.notification, recips, this.contentManager);
  }

  @Test
  public void buildEmail() throws EmailException, StorageClientException, AccessDeniedException, MessagingException {
    List<String> recips = Arrays.asList("joe@media.berkeley.edu", "not.an.email");
    Content sender = new Content("/user1", ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) "user"));
    sender.setProperty("value", "chris@media.berkeley.edu");
    when(this.contentManager.get(LitePersonalUtils.getProfilePath("904715") + NotificationEmailSender.EMAIL_NODE_PATH)).thenReturn(sender);

    MultiPartEmail email = this.sender.buildEmail(this.notification, recips, this.contentManager);
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
