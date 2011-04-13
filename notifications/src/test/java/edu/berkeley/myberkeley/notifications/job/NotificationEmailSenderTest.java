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
import edu.berkeley.myberkeley.caldav.CalDavException;
import edu.berkeley.myberkeley.notifications.Notification;
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

public class NotificationEmailSenderTest extends NotificationTests {

  private NotificationEmailSender sender;

  private Notification notification;

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
    this.notification = new Notification(new JSONObject(readNotificationFromFile()));

    this.sender.smtpServer = "localhost";
    this.sender.smtpPort = 25;
    this.sender.maxRetries = 1;
    this.sender.retryInterval = 60;
    this.sender.sendEmail = true;
  }

  @Test
  public void activate() throws Exception {
    Dictionary<String, Object> dictionary = new Hashtable<String, Object>();
    dictionary.put(NotificationEmailSender.MAX_RETRIES, 10);
    dictionary.put(NotificationEmailSender.RETRY_INTERVAL, 5);
    dictionary.put(NotificationEmailSender.SEND_EMAIL, false);
    dictionary.put(NotificationEmailSender.SMTP_PORT, 27);
    dictionary.put(NotificationEmailSender.SMTP_SERVER, "anotherhost");
    when(componentContext.getProperties()).thenReturn(dictionary);
    this.sender.activate(componentContext);

    assertEquals(this.sender.maxRetries, (Integer) 10);
    assertEquals(this.sender.retryInterval, (Integer) 5);
    assertEquals(this.sender.sendEmail, false);
    assertEquals(this.sender.smtpPort, (Integer) 27);
    assertEquals(this.sender.smtpServer, "anotherhost");
  }

  @Test
  public void deactivate() throws Exception {
    this.sender.deactivate(componentContext);
  }

  @Test
  public void send() throws StorageClientException, AccessDeniedException, IOException, JSONException, CalDavException {
    Session adminSession = mock(Session.class);
    when(this.sender.repository.loginAdministrative()).thenReturn(adminSession);
    when(adminSession.getContentManager()).thenReturn(this.contentManager);

    Content firstRecip = new Content("/user1", ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) "user"));
    firstRecip.setProperty(LitePersonalUtils.PROP_EMAIL_ADDRESS, "user@foo");
    when(this.contentManager.get(LitePersonalUtils.getProfilePath("904715"))).thenReturn(firstRecip);

    List<String> recipients = Arrays.asList("904715");
    this.sender.send(this.notification, recipients);

  }

  @Test(expected = EmailException.class)
  public void buildEmailWithBogusSender() throws EmailException, StorageClientException, AccessDeniedException {
    List<String> recips = Arrays.asList("user@foo.com");
    Content badSender = new Content("/user1", ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) "user"));
    badSender.setProperty(LitePersonalUtils.PROP_EMAIL_ADDRESS, "not an email");
    when(this.contentManager.get(LitePersonalUtils.getProfilePath("904715"))).thenReturn(badSender);
    this.sender.buildEmail(this.notification, recips, this.contentManager);
  }

  @Test
  public void buildEmail() throws EmailException, StorageClientException, AccessDeniedException {
    List<String> recips = Arrays.asList("user@foo.com", "not.an.email");
    Content sender = new Content("/user1", ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) "user"));
    sender.setProperty(LitePersonalUtils.PROP_EMAIL_ADDRESS, "sender@myberkeley.edu");
    when(this.contentManager.get(LitePersonalUtils.getProfilePath("904715"))).thenReturn(sender);

    MultiPartEmail email = this.sender.buildEmail(this.notification, recips, this.contentManager);
    assertNotNull(email);

  }


}
