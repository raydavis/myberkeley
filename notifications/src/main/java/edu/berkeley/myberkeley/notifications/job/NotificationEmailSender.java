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
import org.apache.felix.scr.annotations.Reference;
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
import java.util.Collection;
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

  static final String EMAIL_NODE_PATH = "/basic/elements/email";
  static final String MYBERKELEY_PARTICIPANT_NODE_PATH = "/myberkeley/elements/participant";

  @Property(value = "localhost", label = "SMTP Server")
  static final String SMTP_SERVER = "smtp.server";
  @Property(intValue = 25, label = "SMTP Port")
  static final String SMTP_PORT = "smtp.port";
  @Property(intValue = 240, label = "Maximum number of retries")
  static final String MAX_RETRIES = "email.maxRetries";
  @Property(intValue = 30, label = "Retry interval in minutes")
  static final String RETRY_INTERVAL = "email.retryIntervalMinutes";
  @Property(boolValue = false, label = "Send email?", description = "Whether or not to actually send real email")
  static final String SEND_EMAIL = "email.sendEmailEnabled";

  Integer maxRetries;
  Integer smtpPort;
  String smtpServer;
  Integer retryInterval;
  boolean sendEmail;

  @Reference
  Repository repository;

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEmailSender.class);

  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    this.maxRetries = (Integer) props.get(MAX_RETRIES);
    this.retryInterval = (Integer) props.get(RETRY_INTERVAL);
    this.smtpPort = (Integer) props.get(SMTP_PORT);
    this.smtpServer = (String) props.get(SMTP_SERVER);
    this.sendEmail = (Boolean) props.get(SEND_EMAIL);
  }

  @SuppressWarnings({"UnusedParameters"})
  protected void deactivate(ComponentContext componentContext) throws Exception {
    // nothing to do
  }

  public String send(Notification notification, Collection<String> recipientIDs) {
    Session adminSession = null;
    String messageID = null;
    try {
      adminSession = this.repository.loginAdministrative();
      ContentManager contentManager = adminSession.getContentManager();
      List<String> recipAddresses = getRecipientEmails(recipientIDs, contentManager);
      MultiPartEmail email = buildEmail(notification, recipAddresses, contentManager);
      if (this.sendEmail && !recipAddresses.isEmpty()) {
        messageID = email.sendMimeMessage();
        LOGGER.info("Sent real email with outgoing message ID = " + messageID);
      } else {
        messageID = "sendEmail is false, email not sent";
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
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("NotificationEmailSender failed to log out of admin session", e);
        }
      }
    }
    return messageID;
  }

  private List<String> getRecipientEmails(Collection<String> recipientIDs, ContentManager contentManager) throws StorageClientException, AccessDeniedException {
    List<String> emails = new ArrayList<String>();
    for (String id : recipientIDs) {
      if (isParticipant(id, contentManager)) {
        emails.add(userIDToEmail(id, contentManager));
      }
    }
    LOGGER.info("Recipient email addresses: " + emails);
    return emails;
  }

  private boolean isParticipant(String id, ContentManager contentManager) throws StorageClientException, AccessDeniedException {
    String participantPath = LitePersonalUtils.getProfilePath(id) + MYBERKELEY_PARTICIPANT_NODE_PATH;
    Content content = contentManager.get(participantPath);
    if (content != null) {
      return Boolean.valueOf((String) content.getProperty("value"));
    }
    return false;
  }

  private String userIDToEmail(String id, ContentManager contentManager) throws StorageClientException, AccessDeniedException {
    String recipBasicProfilePath = LitePersonalUtils.getProfilePath(id) + EMAIL_NODE_PATH;
    Content content = contentManager.get(recipBasicProfilePath);
    if (content != null) {
      // TODO is there a better way to get emails out of profiles?
      return (String) content.getProperty("value");
    }
    return null;
  }

  MultiPartEmail buildEmail(Notification notification, List<String> recipientEmails, ContentManager contentManager)
          throws StorageClientException, AccessDeniedException, EmailException, MessagingException {
    MultiPartEmail email = new MultiPartEmail();

    // sender
    try {
      String senderEmail = userIDToEmail(notification.getSenderID(), contentManager);
      email.setFrom(senderEmail);
      email.addBcc(senderEmail); // advisors get a bcc of the email too
    } catch (EmailException e) {
      LOGGER.error("Fatal: Invalid sender email address for user id [" + notification.getSenderID() + "] :" + e);
      throw e;
    }

    // recipients (all are in bcc field)
    for (String recipient : recipientEmails) {
      try {
        email.addBcc(recipient);
      } catch (EmailException e) {
        // just skip invalid email addrs
        LOGGER.warn("Invalid recipient email address [" + recipient + "] :" + e);
      }
    }

    // body and subject
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

