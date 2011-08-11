package edu.berkeley.myberkeley.notifications.job;

import edu.berkeley.myberkeley.notifications.CalendarNotification;
import edu.berkeley.myberkeley.notifications.MessageNotification;
import edu.berkeley.myberkeley.notifications.Notification;
import net.fortuna.ical4j.model.component.VToDo;
import org.apache.commons.lang.time.FastDateFormat;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.jcr.RepositoryException;
import javax.mail.MessagingException;

@Component(label = "MyBerkeley :: ReceiptEmailer",
        description = "Sends receipts for notifications to their senders",
        immediate = true, metatype = true)
@Service(value = ReceiptEmailer.class)
public class ReceiptEmailer {

  @Reference
  SlingRepository slingRepository;

  @Reference
  Repository repository;

  @Reference
  EmailSender emailSender;

  private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptEmailer.class);

  private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("EEE, MMM d, yyyy hh:mm:ss z");

  public void send(Notification notification, Collection<String> recipientIDs) {

    Session adminSession = null;
    javax.jcr.Session slingSession = null;

    try {
      adminSession = this.repository.loginAdministrative();
      slingSession = this.slingRepository.loginAdministrative(null);
      List<String> recipAddresses = getRecipientEmails(recipientIDs, adminSession, slingSession);
      MultiPartEmail email = buildEmail(notification, recipAddresses, adminSession, slingSession);
      this.emailSender.send(email);

    } catch (AccessDeniedException e) {
      LOGGER.error("ReceiptEmailer failed", e);
    } catch (StorageClientException e) {
      LOGGER.error("ReceiptEmailer failed", e);
    } catch (EmailException e) {
      LOGGER.error("ReceiptEmailer failed", e);
    } catch (MessagingException e) {
      LOGGER.error("ReceiptEmailer failed", e);
    } catch (RepositoryException e) {
      LOGGER.error("ReceiptEmailer failed", e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("ReceiptEmailer failed to log out of admin session", e);
        }
      }
      if (slingSession != null) {
        slingSession.logout();
      }
    }

  }

  private List<String> getRecipientEmails(Collection<String> recipientIDs, Session sparseSession, javax.jcr.Session jcrSession) throws StorageClientException, AccessDeniedException, RepositoryException {
    List<String> emails = new ArrayList<String>();
    for (String id : recipientIDs) {
      emails.add(this.emailSender.userIDToEmail(id, sparseSession, jcrSession));
    }
    LOGGER.info("Sending email to the following recipients: " + emails);
    return emails;
  }

  private MultiPartEmail buildEmail(Notification notification, List<String> recipientEmails, Session sparseSession, javax.jcr.Session jcrSession)
          throws StorageClientException, AccessDeniedException, EmailException, MessagingException, RepositoryException {
    MultiPartEmail email = new MultiPartEmail();

    // sender and recipient are the same
    try {
      String senderEmail = this.emailSender.userIDToEmail(notification.getSenderID(), sparseSession, jcrSession);
      email.setFrom(senderEmail);
      email.addTo(senderEmail);
    } catch (EmailException e) {
      LOGGER.error("Fatal: Invalid sender email address for user id [" + notification.getSenderID() + "] :" + e);
      throw e;
    }

    // body and subject
    StringBuilder msg = new StringBuilder("This is an automated message from CalCentral.\n\n");
    String type = "Task";

    if ( notification instanceof CalendarNotification ) {
      CalendarNotification calendarNotification = (CalendarNotification) notification;
      if (calendarNotification.getWrapper().getComponent() instanceof VToDo) {
        email.setSubject("CalCentral has delivered your task");
        msg.append("CalCentral delivered this task:");
      } else {
        type = "Event";
        email.setSubject("CalCentral has delivered your event");
        msg.append("CalCentral delivered this event:");
      }
    } else {
      type = "Message";
      email.setSubject("CalCentral has delivered your message");
      msg.append("CalCentral delivered this message:");
    }

    msg.append("\n");

    if ( notification instanceof CalendarNotification ) {
      CalendarNotification calendarNotification = (CalendarNotification) notification;
      msg.append("\n").append(type).append(" Subject: ");
      msg.append(calendarNotification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.SUMMARY).getValue());
      msg.append("\n").append(type).append(" Body: ");
      msg.append(calendarNotification.getWrapper().getComponent().getProperty(net.fortuna.ical4j.model.Property.DESCRIPTION).getValue());

    } else {
      MessageNotification msgNotification = (MessageNotification) notification;
      msg.append("\n").append(type).append(" Subject: ");
      msg.append(msgNotification.getSubject());
      msg.append("\n").append(type).append(" Body: ");
      msg.append(msgNotification.getBody());
    }

    msg.append("\n\nto the following student(s) at ");
    msg.append(DATE_FORMAT.format(new Date())).append(":\n\n");

    for ( String recip : recipientEmails ) {
      msg.append(recip).append("\n");
    }

    msg.append("\n\nTo see all of the tasks, events and messages you've created: \n\n" +
            "  * Log on to CalCentral at http://calcentral.berkeley.edu \n" +
            "  * On the top menu, click Me. \n" +
            "  * On the left navigation menu, click Notifications. \n" +
            "  * Choose Drafts, Queue, Archive or Trash.\n\n");

    email.setMsg(msg.toString());
    this.emailSender.prepareMessage(email);

    return email;
  }

}
