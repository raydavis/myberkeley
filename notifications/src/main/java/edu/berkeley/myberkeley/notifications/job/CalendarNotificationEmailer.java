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
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VToDo;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.activation.DataSource;
import javax.jcr.RepositoryException;
import javax.mail.MessagingException;

@Component(label = "MyBerkeley :: CalendarNotificationEmailer",
        description = "Sends emails for notifications",
        immediate = true, metatype = true)
@Service(value = CalendarNotificationEmailer.class)
public class CalendarNotificationEmailer {

  private static final String REMINDER_RECIPIENT = "reminder-recipient:;";

  @Reference
  SlingRepository slingRepository;

  @Reference
  Repository repository;

  @Reference
  EmailSender emailSender;

  private static final Logger LOGGER = LoggerFactory.getLogger(CalendarNotificationEmailer.class);

  public String send(CalendarNotification notification, Collection<String> recipientIDs) {
    Session adminSession = null;
    javax.jcr.Session slingSession = null;
    String messageID = null;
    try {
      adminSession = this.repository.loginAdministrative();
      slingSession = this.slingRepository.loginAdministrative(null);
      List<String> recipAddresses = getRecipientEmails(recipientIDs, adminSession, slingSession);
      MultiPartEmail email = buildEmail(notification, recipAddresses, adminSession, slingSession);
      messageID = this.emailSender.send(email);

    } catch (AccessDeniedException e) {
      LOGGER.error("CalendarNotificationEmailer failed", e);
    } catch (StorageClientException e) {
      LOGGER.error("CalendarNotificationEmailer failed", e);
    } catch (EmailException e) {
      LOGGER.error("CalendarNotificationEmailer failed", e);
    } catch (MessagingException e) {
      LOGGER.error("CalendarNotificationEmailer failed", e);
    } catch (RepositoryException e) {
      LOGGER.error("CalendarNotificationEmailer failed", e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("CalendarNotificationEmailer failed to log out of admin session", e);
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
      emails.add(this.emailSender.userIDToEmail(id, sparseSession, jcrSession));
    }
    LOGGER.info("Sending email to the following recipients: " + emails);
    return emails;
  }

  MultiPartEmail buildEmail(CalendarNotification notification, List<String> recipientEmails, Session sparseSession, javax.jcr.Session jcrSession)
          throws StorageClientException, AccessDeniedException, EmailException, MessagingException, RepositoryException {
    MultiPartEmail email = new MultiPartEmail();

    // sender
    try {
      String senderEmail = this.emailSender.userIDToEmail(notification.getSenderID(), sparseSession, jcrSession);
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

    boolean isTask = notification.getWrapper().getComponent() instanceof VToDo;
    String type = "Task";

    // subject line
    StringBuilder subject = new StringBuilder("[CalCentral");
    if (isTask) {
      subject.append(" task] You have a new task titled \"");
    } else {
      type = "Event";
      subject.append(" event] You have a new event titled \"");
    }

    subject.append(notification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.SUMMARY).getValue());
    subject.append("\"");
    email.setSubject(subject.toString());

    // email body
    StringBuilder msg = new StringBuilder("This is an automated message from CalCentral.\n\n");
    if (isTask) {
      msg.append("A new task has been added to your My Tasks widget:\n\n");
    } else {
      msg.append("A new event has been added to your My Events widget:\n\n");
    }

    msg.append(type).append(" Subject: ").append(notification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.SUMMARY).getValue()).append("\n");
    msg.append(type).append(" Body: ").append(notification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.DESCRIPTION).getValue()).append("\n\n");

    msg.append("To view this ").append(type).append(" in CalCentral:\n\n");
    msg.append("* Log on to CalCentral at http://calcentral.berkeley.edu \n");

    if (isTask) {
      msg.append("* If necessary, scroll down to see your My Tasks widget. \n" +
              "* Click on a task's title to see details about that task.\n\n");
      msg.append("If you have a standards-compliant calendar system, you can add this " +
              "task by opening the enclosed file whose name ends with \".ics.\" ");
      msg.append(" You can keep your task list current by checking off tasks in your CalCentral My Tasks widget as you complete them.");
    } else {
      msg.append("* If necessary, scroll down to see your My Events widget. \n" +
              "* Click on an event's title to see details about that event.\n\n");
      msg.append("If you have a standards-compliant calendar system, you can add this " +
              "event by opening the enclosed file whose name ends with \".ics.\" ");
    }
    msg.append("\n\n");

    email.setMsg(msg.toString());

    // attach .ics file
    CalendarDatasource datasource = new CalendarDatasource(notification.getWrapper());
    email.attach(datasource, "CalCentral-" + datasource.getName(), ".ics calendar file");

    this.emailSender.prepareMessage(email);

    // adding this special recipient here as header directly, otherwise address parsing will fail
    email.getMimeMessage().addHeader("To", REMINDER_RECIPIENT);

    return email;
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

