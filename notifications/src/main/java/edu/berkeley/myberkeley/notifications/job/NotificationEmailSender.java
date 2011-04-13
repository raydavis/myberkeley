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

import edu.berkeley.myberkeley.notifications.Notification;
import net.fortuna.ical4j.model.component.VToDo;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Component(label = "MyBerkeley :: NotificationEmailSender",
        description = "Sends emails for notifications",
        immediate = true, metatype = true)
@Service(value = NotificationEmailSender.class)
public class NotificationEmailSender {

  // Subject prefixes (each of them must end with a space character)
  private static final String SUBJECT_PREFIX_TASK = "[myB-task] ";
  private static final String SUBJECT_PREFIX_TASK_REQUIRED = "[myB-task-required] ";
  private static final String SUBJECT_PREFIX_EVENT = "[myB-event] ";
  private static final String SUBJECT_PREFIX_EVENT_REQUIRED = "[myB-event-required] ";
  private static final String REMINDER_RECIPIENT = "reminder-recipient:;";

  @Property(value = "localhost")
  static final String SMTP_SERVER = "sakai.smtp.server";
  @Property(intValue = 25, label = "%sakai.smtp.port.name")
  static final String SMTP_PORT = "sakai.smtp.port";
  @Property(intValue = 240)
  static final String MAX_RETRIES = "sakai.email.maxRetries";
  @Property(intValue = 30)
  static final String RETRY_INTERVAL = "sakai.email.retryIntervalMinutes";
  @Property(boolValue = false, label = "%sakai.email.sendemail.name", description = "%sakai.email.sendemail.description")
  static final String SEND_EMAIL = "notifications.sendEmail";

  Integer maxRetries;
  Integer smtpPort;
  String smtpServer;
  Integer retryInterval;
  boolean sendEmail;

  Repository repository;

  private final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsScheduler.class);

  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    this.maxRetries = (Integer) props.get(MAX_RETRIES);
    this.retryInterval = (Integer) props.get(RETRY_INTERVAL);
    this.smtpPort = (Integer) props.get(SMTP_PORT);
    this.smtpServer = (String) props.get(SMTP_SERVER);
    this.sendEmail = (Boolean) props.get(SEND_EMAIL);
  }

  protected void deactivate(ComponentContext componentContext) throws Exception {
    // nothing to do
  }

  public void send(Notification notification, List<String> recipientIDs) {
    Session adminSession = null;
    try {
      adminSession = this.repository.loginAdministrative();
      List<String> recipAddresses = getRecipientEmails(adminSession, recipientIDs);
      MultiPartEmail email = buildEmail(notification, recipAddresses, adminSession.getContentManager());
      if ( this.sendEmail ) {
        String messageID = email.sendMimeMessage();
        LOGGER.info("Sent real email with outgoing message ID = " + messageID);
      } else {
        LOGGER.info("sendEmail is false, not sending mail");
      }
    } catch (AccessDeniedException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } catch (StorageClientException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } catch (EmailException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } catch (MessagingException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    }finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("NotificationEmailSender failed to log out of admin session", e);
        }
      }
    }
  }

  private List<String> getRecipientEmails(Session adminSession, List<String> recipientIDs) throws StorageClientException, AccessDeniedException {
    List<String> emails = new ArrayList<String>();
    ContentManager contentManager = adminSession.getContentManager();
    for (String id : recipientIDs) {
      emails.add(userIDToEmail(contentManager, id));
    }
    LOGGER.info("Recipient email addresses: " + emails);
    return emails;
  }

  private String userIDToEmail(ContentManager contentManager, String id) throws StorageClientException, AccessDeniedException {
    String recipientPath = LitePersonalUtils.getProfilePath(id);
    Content content = contentManager.get(recipientPath);
    return LitePersonalUtils.getPrimaryEmailAddress(content);
  }

  MultiPartEmail buildEmail(Notification notification, List<String> recipientEmails, ContentManager contentManager)
          throws StorageClientException, AccessDeniedException, EmailException, MessagingException {
    MultiPartEmail email = new MultiPartEmail();
    for (String recipient : recipientEmails) {
      try {
        email.addBcc(recipient);
      } catch (EmailException e) {
        // just skip invalid email addrs
        LOGGER.warn("Invalid recipient email address [" + recipient + "] :" + e);
      }
    }

    try {
      email.setFrom(userIDToEmail(contentManager, notification.getSenderID()));
    } catch (EmailException e) {
      LOGGER.error("Fatal: Invalid sender email address for user id [" + notification.getSenderID() + "] :" + e);
      throw e;
    }

    // TODO convert calendarWrapper to a nice email
    email.setMsg(notification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.DESCRIPTION).getValue());

    String subjectPrefix = getSubjectPrefix(notification);
    email.setSubject(subjectPrefix + " " + notification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.SUMMARY).getValue());

    email.setDebug(true);
    email.setSmtpPort(this.smtpPort);
    email.setHostName(this.smtpServer);
    email.buildMimeMessage();
    // adding this special recipient here as header directly, otherwise address parsing will fail
    email.getMimeMessage().addHeader("To", REMINDER_RECIPIENT);

    logEmail(email.getMimeMessage());
    return email;
  }

  private String getSubjectPrefix(Notification notification) {
    if (notification.getWrapper().isRequired()) {
      if (notification.getWrapper().getComponent() instanceof VToDo) {
        return SUBJECT_PREFIX_TASK_REQUIRED;
      } else {
        return SUBJECT_PREFIX_EVENT_REQUIRED;
      }
    } else {
      if (notification.getWrapper().getComponent() instanceof VToDo) {
        return SUBJECT_PREFIX_TASK;
      } else {
        return SUBJECT_PREFIX_EVENT;
      }
    }
  }

  private void logEmail(MimeMessage mimeMessage) {
    if (LOGGER.isInfoEnabled() && mimeMessage != null) {
      try {
        ByteArrayOutputStream mout = new ByteArrayOutputStream();
        mimeMessage.writeTo(new FilterOutputStream(mout));
        LOGGER.info("Email content = " + mout.toString());
      } catch (IOException e) {
        LOGGER.error("failed to log email", e);
      } catch (MessagingException e) {
        LOGGER.error("failed to log email", e);
      }
    }
  }

}

