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

import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import edu.berkeley.myberkeley.notifications.CalendarNotification;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VToDo;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import javax.activation.DataSource;
import javax.jcr.RepositoryException;
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

  static final String MYBERKELEY_PARTICIPANT_NODE_PATH = "/myberkeley/elements/participant";

  @Property(value = "localhost", label = "SMTP Server")
  static final String SMTP_SERVER = "smtp.server";
  @Property(intValue = 25, label = "SMTP Port")
  static final String SMTP_PORT = "smtp.port";
  @Property(boolValue = false, label = "Send email?", description = "Whether or not to actually send real email")
  static final String SEND_EMAIL = "email.sendEmailEnabled";

  Integer smtpPort;
  String smtpServer;
  boolean sendEmail;

  @Reference
  SlingRepository slingRepository;

  @Reference
  Repository repository;

  @Reference
  ProfileService profileService;

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEmailSender.class);

  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    this.smtpPort = OsgiUtil.toInteger(props.get(SMTP_PORT), 25);
    this.smtpServer = OsgiUtil.toString(props.get(SMTP_SERVER), "");
    this.sendEmail = OsgiUtil.toBoolean(props.get(SEND_EMAIL), false);
  }

  @SuppressWarnings({"UnusedParameters"})
  protected void deactivate(ComponentContext componentContext) throws Exception {
    // nothing to do
  }

  public String send(CalendarNotification notification, Collection<String> recipientIDs) {
    Session adminSession = null;
    javax.jcr.Session slingSession = null;
    String messageID = null;
    try {
      adminSession = this.repository.loginAdministrative();
      slingSession = this.slingRepository.loginAdministrative(null);
      List<String> recipAddresses = getRecipientEmails(recipientIDs, adminSession, slingSession);
      MultiPartEmail email = buildEmail(notification, recipAddresses, adminSession, slingSession);
      if (this.sendEmail && !recipAddresses.isEmpty()) {
        messageID = email.sendMimeMessage();
        LOGGER.info("Sent real email with outgoing message ID = " + messageID);
      } else {
        messageID = "sendEmail is false, email not sent";
        LOGGER.info("sendEmail is false, not actually sending mail");
      }
    } catch (AccessDeniedException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } catch (StorageClientException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } catch (EmailException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } catch (MessagingException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } catch (RepositoryException e) {
      LOGGER.error("NotificationEmailSender failed", e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("NotificationEmailSender failed to log out of admin session", e);
        }
      }
      if (slingSession != null) {
        slingSession.logout();
      }
    }
    return messageID;
  }

  private List<String> getRecipientEmails(Collection<String> recipientIDs, Session sparseSession, javax.jcr.Session jcrSession) throws StorageClientException, AccessDeniedException, RepositoryException {
    List<String> emails = new ArrayList<String>();
    for (String id : recipientIDs) {
      if (isParticipant(id, sparseSession.getContentManager())) {
        emails.add(userIDToEmail(id, sparseSession, jcrSession));
      }
    }
    LOGGER.info("Sending email to the following recipients: " + emails);
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

  private String userIDToEmail(String id, Session sparseSession, javax.jcr.Session jcrSession) throws StorageClientException, AccessDeniedException, RepositoryException {
    AuthorizableManager authMgr = sparseSession.getAuthorizableManager();
    Authorizable authz = authMgr.findAuthorizable(id);
    ValueMap profile = this.profileService.getProfileMap(authz, jcrSession);
    // TODO we depend heavily on the profile ValueMap structure being correct. we find our email addresses
    // in the path /profile/basic/elements/email/value . This code will break when we revise our profile structure.
    Map basic = (Map) profile.get("basic");
    Map elements = (Map) basic.get("elements");
    Map email = (Map) elements.get("email");
    return (String) email.get("value");
  }

  MultiPartEmail buildEmail(CalendarNotification notification, List<String> recipientEmails, Session sparseSession, javax.jcr.Session jcrSession)
          throws StorageClientException, AccessDeniedException, EmailException, MessagingException, RepositoryException {
    MultiPartEmail email = new MultiPartEmail();

    // sender
    try {
      String senderEmail = userIDToEmail(notification.getSenderID(), sparseSession, jcrSession);
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
    email.setMsg(notification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.DESCRIPTION).getValue());
    String subjectPrefix = getSubjectPrefix(notification);
    email.setSubject(subjectPrefix + " " + notification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.SUMMARY).getValue());

    // attach .ics file
    CalendarDatasource datasource = new CalendarDatasource(notification.getWrapper());
    email.attach(datasource, datasource.getName(), ".ics calendar file");

    email.setDebug(true);
    email.setSmtpPort(this.smtpPort);
    email.setHostName(this.smtpServer);

    email.buildMimeMessage();
    // adding this special recipient here as header directly, otherwise address parsing will fail
    email.getMimeMessage().addHeader("To", REMINDER_RECIPIENT);
    logEmail(email.getMimeMessage());
    return email;
  }

  private String getSubjectPrefix(CalendarNotification notification) {
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
    if ((!this.sendEmail || LOGGER.isDebugEnabled()) && mimeMessage != null) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(new FilterOutputStream(baos));
        if (this.sendEmail) {
          LOGGER.debug("Email content = " + baos.toString());
        } else {
          LOGGER.info("Email content = " + baos.toString());
        }
      } catch (IOException e) {
        LOGGER.error("failed to log email", e);
      } catch (MessagingException e) {
        LOGGER.error("failed to log email", e);
      }
    }
  }

  private class CalendarDatasource implements DataSource {

    private CalendarWrapper calendarWrapper;

    public CalendarDatasource(CalendarWrapper wrapper) {
      this.calendarWrapper = wrapper;
    }

    public InputStream getInputStream() throws IOException {
      CalendarOutputter outputter = new CalendarOutputter();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
        outputter.output(this.calendarWrapper.getCalendar(), baos);
        return new ByteArrayInputStream(baos.toByteArray());
      } catch (ValidationException e) {
        throw new IOException(e);
      }
    }

    public OutputStream getOutputStream() throws IOException {
      throw new IOException("This class is read-only");
    }

    public String getContentType() {
      return "text/calendar";
    }

    public String getName() {
      return this.calendarWrapper.getComponent().getProperty(net.fortuna.ical4j.model.Property.UID).getValue() + ".ics";
    }
  }
}

