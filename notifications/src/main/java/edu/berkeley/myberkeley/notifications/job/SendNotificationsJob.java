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

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import edu.berkeley.myberkeley.caldav.api.BadRequestException;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavConnectorProvider;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.notifications.CalendarNotification;
import edu.berkeley.myberkeley.notifications.MessageNotification;
import edu.berkeley.myberkeley.notifications.Notification;
import edu.berkeley.myberkeley.notifications.NotificationFactory;
import edu.berkeley.myberkeley.notifications.RecipientLog;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.message.LiteMessagingService;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.RepositoryException;

public class SendNotificationsJob implements Job {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsJob.class);

  final Repository sparseRepository;

  final CalendarNotificationEmailer calendarEmailer;

  final ReceiptEmailer receiptEmailer;

  final CalDavConnectorProvider calDavConnectorProvider;

  final DynamicListService dynamicListService;

  final LiteMessagingService messagingService;

  public SendNotificationsJob(Repository sparseRepository, CalendarNotificationEmailer calendarEmailer,
                              ReceiptEmailer receiptEmailer,
                              CalDavConnectorProvider calDavConnectorProvider, DynamicListService dynamicListService,
                              LiteMessagingService messagingService) {
    this.sparseRepository = sparseRepository;
    this.calendarEmailer = calendarEmailer;
    this.receiptEmailer = receiptEmailer;
    this.calDavConnectorProvider = calDavConnectorProvider;
    this.dynamicListService = dynamicListService;
    this.messagingService = messagingService;
  }

  public void execute(JobContext jobContext) {
    long startMillis = System.currentTimeMillis();

    Session adminSession = null;

    try {
      adminSession = this.sparseRepository.loginAdministrative();
      Iterable<Content> results = getQueuedNotifications(adminSession);
      processResults(results, adminSession);
    } catch (AccessDeniedException e) {
      LOGGER.error("SendNotificationsJob failed", e);
    } catch (StorageClientException e) {
      LOGGER.error("SendNotificationsJob failed", e);
    } catch (IOException e) {
      LOGGER.error("SendNotificationsJob failed", e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error("SendNotificationsJob failed to log out of admin session", e);
        }
      }
      long endMillis = System.currentTimeMillis();
      LOGGER.debug("SendNotificationsJob executed in {} ms ", (endMillis - startMillis));
    }
  }

  private Iterable<Content> getQueuedNotifications(Session adminSession) throws StorageClientException, AccessDeniedException {
    ContentManager cm = adminSession.getContentManager();
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("sakai:messagebox", Notification.MESSAGEBOX.queue.toString());
    props.put("sling:resourceType", Notification.RESOURCETYPE);
    props.put("_items", 50000); // make sure we get all our queued notifications
    return cm.find(props);
  }

  private void processResults(Iterable<Content> results, Session session) throws IOException {
    Date now = new Date();
    int resultCount = 0;
    int eligibleCount = 0;
    for (Content result : results) {
      resultCount++;
      if (eligibleForSending(result, now)) {
        eligibleCount++;
        LOGGER.info("The time has come to send notification at path " + result.getPath());
        sendNotification(result, session);
      }
    }
    LOGGER.debug("Found " + resultCount + " results, of which " + eligibleCount + " were eligible for sending");
  }

  private boolean eligibleForSending(Content result, Date now) {
    Object sendDateValue = result.getProperty(Notification.JSON_PROPERTIES.sendDate.toString());
    Date sendDate = new ISO8601Date(sendDateValue.toString()).getTime();
    Notification.MESSAGEBOX box = Notification.MESSAGEBOX.valueOf((String) result.getProperty(Notification.JSON_PROPERTIES.messageBox.toString()));
    Notification.SEND_STATE sendState = Notification.SEND_STATE.valueOf((String) result.getProperty(Notification.JSON_PROPERTIES.sendState.toString()));
    return Notification.MESSAGEBOX.queue.equals(box) && Notification.SEND_STATE.pending.equals(sendState) && now.compareTo(sendDate) >= 0;
  }

  private void sendNotification(Content result, Session adminSession) throws IOException {

    boolean success = false;
    Notification notification = null;
    RecipientLog recipientLog = null;
    Session userSession = null;
    Collection<String> userIDs = null;
    String errMsg = null;

    try {
      notification = NotificationFactory.getFromContent(result);
      recipientLog = new RecipientLog(result.getPath(), adminSession);
    } catch (JSONException e) {
      errMsg = "Notification at path " + result.getPath() + " has invalid JSON for calendarWrapper";
      LOGGER.error(errMsg, e);
    } catch (CalDavException e) {
      errMsg = "Notification at path " + result.getPath() + " has invalid calendar data";
      LOGGER.error(errMsg, e);
    } catch (AccessDeniedException e) {
      errMsg = "Got error fetching log for notification at path " + result.getPath();
      LOGGER.error(errMsg, e);
    } catch (StorageClientException e) {
      errMsg = "Got error fetching log for notification at path " + result.getPath();
      LOGGER.error(errMsg, e);
    }

    if (notification != null && recipientLog != null) {
      try {

        Content dynamicList = adminSession.getContentManager().get(PathUtils.toUserContentPath(notification.getDynamicListID()));
        if (dynamicList == null) {
          errMsg = "Dynamic list is null for notification at path " + result.getPath()
                  + "; dynamic list path = " + notification.getDynamicListID();
          LOGGER.error(errMsg);
        } else {

          userIDs = this.dynamicListService.getUserIdsForNode(dynamicList, adminSession);
          LOGGER.info("Dynamic list includes these user ids: " + userIDs);

          if (notification instanceof CalendarNotification) {
            success = sendCalendarNotification(result, (CalendarNotification) notification, recipientLog, userIDs);
          } else if (notification instanceof MessageNotification) {
            userSession = this.sparseRepository.loginAdministrative(notification.getSenderID());
            success = sendMessageNotification(result, (MessageNotification) notification, recipientLog, userIDs, userSession);
          }
        }
      } catch (JSONException e) {
        errMsg = "Notification at path " + result.getPath() + " has invalid JSON for calendarWrapper";
        LOGGER.error(errMsg, e);
      } catch (BadRequestException e) {
        errMsg = "Got bad request from CalDav server trying to post notification at path " + result.getPath();
        LOGGER.error(errMsg, e);
      } catch (CalDavException e) {
        errMsg = "Notification at path " + result.getPath() + " has invalid calendar data";
        LOGGER.error(errMsg, e);
      } catch (RepositoryException e) {
        errMsg = "Got repo exception processing notification at path " + result.getPath();
        LOGGER.error(errMsg, e);
      } catch (StorageClientException e) {
        errMsg = "Got StorageClientException fetching filter criteria for notification at path " + result.getPath();
        LOGGER.error(errMsg, e);
      } catch (AccessDeniedException e) {
        errMsg = "Got AccessDeniedException fetching filter criteria for notification at path " + result.getPath();
        LOGGER.error(errMsg, e);
      } finally {
        if (userSession != null) {
          try {
            userSession.logout();
          } catch (ClientPoolException e) {
            LOGGER.error("SendNotificationsJob failed to log out of user session", e);
          }
        }
      }
    }

    // mark the notification as archived in our repo if all went well
    if (success) {
      result.setProperty(Notification.JSON_PROPERTIES.messageBox.toString(), Notification.MESSAGEBOX.archive.toString());
      result.setProperty(Notification.JSON_PROPERTIES.sendState.toString(), Notification.SEND_STATE.sent.toString());
      this.receiptEmailer.send(notification, userIDs);
    } else {
      // mark it errored so we don't try to send it again
      result.setProperty(Notification.JSON_PROPERTIES.errMsg.toString(), errMsg);
      result.setProperty(Notification.JSON_PROPERTIES.sendState.toString(), Notification.SEND_STATE.error.toString());
    }

    try {
      if (recipientLog != null) {
        recipientLog.update(adminSession.getContentManager());
      }
      adminSession.getContentManager().update(result);
    } catch (AccessDeniedException e) {
      LOGGER.error("Got access denied saving notification at path " + result.getPath(), e);
    } catch (StorageClientException e) {
      LOGGER.error("Got storage client exception saving notification at path " + result.getPath(), e);
    }

  }

  @SuppressWarnings({"DuplicateThrows"})
  private boolean sendCalendarNotification(Content result, CalendarNotification notification, RecipientLog recipientLog, Collection<String> userIDs)
          throws BadRequestException, CalDavException, IOException, JSONException {
    // save notification in bedework server
    for (String userID : userIDs) {
      boolean needsCalendarEntry;
      try {
        needsCalendarEntry = recipientLog.getRecipientToCalendarURIMap().get(userID) == null;
      } catch (JSONException ignored) {
        needsCalendarEntry = true;
      }

      if (needsCalendarEntry) {
        CalDavConnector connector = this.calDavConnectorProvider.getAdminConnector(userID);
        notification.getWrapper().generateNewUID();
        try {
          CalendarURI uri = connector.putCalendar(notification.getWrapper().getCalendar());
          recipientLog.getRecipientToCalendarURIMap().put(userID, uri.toJSON());
        } catch (BadRequestException e) {
          if (HttpStatus.SC_NOT_FOUND == e.getStatusCode()) {
            // 404 means user's not in Bedework, skip it and log
            LOGGER.warn("User {} does not have a Bedework account yet, skipping calendar creation", userID);
          } else {
            throw e;
          }
        }
      }
    }

    // send email
    boolean needsEmail = recipientLog.getEmailMessageID() == null;
    if (needsEmail) {
      String messageID = this.calendarEmailer.send(notification, userIDs);
      if (messageID != null) {
        recipientLog.setEmailMessageID(messageID);
      }
    }

    LOGGER.info("Successfully sent calendar notification; local path " + result.getPath() + "; recipientToCalendarURIMap = "
            + recipientLog.getRecipientToCalendarURIMap().toString(2));
    return true;
  }

  private boolean sendMessageNotification(Content result, MessageNotification notification, RecipientLog recipientLog, Collection<String> userIDs, Session session) throws JSONException {
    // deliver notifications as regular sakai messages

    for (String userID : userIDs) {
      boolean needsMessage;
      try {
        needsMessage = recipientLog.getRecipientToCalendarURIMap().get(userID) == null;
      } catch (JSONException ignored) {
        needsMessage = true;
      }
      if (needsMessage) {
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("sling:resourceType", "sakai/message");
        props.put("sakai:category", "message");
        props.put("_charset_", "utf-8");
        props.put(MessageConstants.PROP_SAKAI_SENDSTATE, MessageConstants.STATE_PENDING);
        props.put(MessageConstants.PROP_SAKAI_BODY, notification.getBody());
        props.put(MessageConstants.PROP_SAKAI_SUBJECT, notification.getSubject());
        props.put(MessageConstants.PROP_SAKAI_TYPE, MessageConstants.TYPE_INTERNAL);
        props.put(MessageConstants.PROP_SAKAI_TO, "internal:" + userID);
        props.put(MessageConstants.PROP_SAKAI_READ, true);
        props.put(MessageConstants.PROP_SAKAI_MESSAGEBOX, MessageConstants.BOX_OUTBOX);
        props.put(MessageConstants.PROP_SAKAI_FROM, notification.getSenderID());
        Content msg = this.messagingService.create(session, props);
        JSONObject logEntry = new JSONObject();
        logEntry.put("messagePath", msg.getPath());
        recipientLog.getRecipientToCalendarURIMap().put(userID, logEntry);
      }
    }

    LOGGER.info("Successfully sent message notification; local path " + result.getPath() + "; recipientToCalendarURIMap = "
            + recipientLog.getRecipientToCalendarURIMap().toString(2));

    return true;
  }
}
