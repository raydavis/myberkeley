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

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListContext;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import edu.berkeley.myberkeley.caldav.api.BadRequestException;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavConnectorProvider;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.notifications.Notification;
import edu.berkeley.myberkeley.notifications.RecipientLog;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.jcr.api.SlingRepository;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

public class SendNotificationsJob implements Job {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsJob.class);

  final Repository sparseRepository;

  final NotificationEmailSender emailSender;

  final CalDavConnectorProvider calDavConnectorProvider;

  final DynamicListService dynamicListService;

  final SlingRepository slingRepository;

  public SendNotificationsJob(Repository sparseRepository, SlingRepository slingRepository, NotificationEmailSender emailSender,
                              CalDavConnectorProvider calDavConnectorProvider, DynamicListService dynamicListService) {
    this.sparseRepository = sparseRepository;
    this.slingRepository = slingRepository;
    this.emailSender = emailSender;
    this.calDavConnectorProvider = calDavConnectorProvider;
    this.dynamicListService = dynamicListService;
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
        LOGGER.debug("The time has come to send notification at path " + result.getPath());
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

  private void sendNotification(Content result, Session session) throws IOException {

    boolean success = false;
    Notification notification;
    RecipientLog recipientLog;

    try {
      notification = new Notification(result);
      recipientLog = new RecipientLog(result.getPath(), session);
    } catch (JSONException e) {
      LOGGER.error("Notification at path " + result.getPath() + " has invalid JSON for calendarWrapper", e);
      return;
    } catch (CalDavException e) {
      LOGGER.error("Notification at path " + result.getPath() + " has invalid calendar data", e);
      return;
    } catch (AccessDeniedException e) {
      LOGGER.error("Got error fetching log for notification at path " + result.getPath(), e);
      return;
    } catch (StorageClientException e) {
      LOGGER.error("Got error fetching log for notification at path " + result.getPath(), e);
      return;
    }

    JSONObject recipientToCalendarURIMap = recipientLog.getRecipientToCalendarURIMap();

    try {

      Content listQuery = session.getContentManager().get(notification.getDynamicListID() + "/query");
      String criteria = (String) listQuery.getProperty("filter");
      String contextName = (String) listQuery.getProperty("context");

      javax.jcr.Session jcrSession = this.slingRepository.loginAdministrative(null);
      Node listContextNode = jcrSession.getNode("/var/myberkeley/dynamiclists/" + contextName);
      DynamicListContext context = new DynamicListContext(listContextNode);

      Collection<String> userIDs = this.dynamicListService.getUserIdsForCriteria(context, criteria);
      LOGGER.info("Dynamic list includes these user ids: " + userIDs);

      // save notification in bedework server
      for (String userID : userIDs) {
        boolean needsCalendarEntry;
        try {
          needsCalendarEntry = recipientToCalendarURIMap.getJSONObject(userID) == null;
        } catch (JSONException ignored) {
          needsCalendarEntry = true;
        }

        if (needsCalendarEntry) {
          CalDavConnector connector = this.calDavConnectorProvider.getAdminConnector(userID);
          notification.getWrapper().generateNewUID();
          try {
            CalendarURI uri = connector.putCalendar(notification.getWrapper().getCalendar());
            recipientToCalendarURIMap.put(userID, uri.toJSON());
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
        String messageID = this.emailSender.send(notification, userIDs);
        if (messageID != null) {
          recipientLog.setEmailMessageID(messageID);
        }
      }

      success = true;
      LOGGER.debug("Successfully sent notification; local path " + result.getPath() + "; recipientToCalendarURIMap = "
              + recipientToCalendarURIMap.toString(2));

    } catch (JSONException e) {
      LOGGER.error("Notification at path " + result.getPath() + " has invalid JSON for calendarWrapper", e);
    } catch (BadRequestException e) {
      LOGGER.error("Got bad request from CalDav server trying to post notification at path " + result.getPath(), e);
    } catch (CalDavException e) {
      LOGGER.error("Notification at path " + result.getPath() + " has invalid calendar data", e);
    } catch (RepositoryException e) {
      LOGGER.error("Got repo exception processing notification at path " + result.getPath(), e);
    } catch (StorageClientException e) {
      LOGGER.error("Got error fetching filter criteria for notification at path " + result.getPath(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error("Got error fetching filter criteria for notification at path " + result.getPath(), e);
    }

    // mark the notification as archived in our repo if all went well
    if (success) {
      result.setProperty(Notification.JSON_PROPERTIES.messageBox.toString(), Notification.MESSAGEBOX.archive.toString());
      result.setProperty(Notification.JSON_PROPERTIES.sendState.toString(), Notification.SEND_STATE.sent.toString());
    }

    try {
      recipientLog.update(session.getContentManager());
      session.getContentManager().update(result);
    } catch (AccessDeniedException e) {
      LOGGER.error("Got access denied saving notification at path " + result.getPath(), e);
    } catch (StorageClientException e) {
      LOGGER.error("Got storage client exception saving notification at path " + result.getPath(), e);
    }

  }

}
