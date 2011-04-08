package edu.berkeley.myberkeley.notifications.job;

import edu.berkeley.myberkeley.caldav.BadRequestException;
import edu.berkeley.myberkeley.caldav.CalDavConnector;
import edu.berkeley.myberkeley.caldav.CalDavException;
import edu.berkeley.myberkeley.caldav.CalendarURI;
import edu.berkeley.myberkeley.caldav.CalendarWrapper;
import edu.berkeley.myberkeley.notifications.Notification;
import org.apache.sling.commons.json.JSONArray;
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
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SendNotificationsJob implements Job {

    private final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsJob.class);

    final Repository repository;

    final CalDavConnectorProvider calDavConnectorProvider;

    public SendNotificationsJob(Repository repository, CalDavConnectorProvider calDavConnectorProvider) {
        this.repository = repository;
        this.calDavConnectorProvider = calDavConnectorProvider;
    }

    public void execute(JobContext jobContext) {
        long startMillis = System.currentTimeMillis();

        Session adminSession = null;

        try {
            adminSession = repository.loginAdministrative();
            Iterable<Content> results = getQueuedNotifications(adminSession);
            processResults(results, adminSession.getContentManager());
        } catch (AccessDeniedException e) {
            LOGGER.error("SendNotificationsJob failed", e);
        } catch (StorageClientException e) {
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
            LOGGER.info("SendNotificationsJob executed in {} ms ", (endMillis - startMillis));
        }
    }

    private Iterable<Content> getQueuedNotifications(Session adminSession) throws StorageClientException, AccessDeniedException {
        ContentManager cm = adminSession.getContentManager();
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("sakai:messagebox", Notification.MESSAGEBOX.queue.toString());
        props.put("sling:resourceType", Notification.RESOURCETYPE);
        return cm.find(props);
    }

    private void processResults(Iterable<Content> results, ContentManager contentManager) {
        Date now = new Date();
        for (Content result : results) {
            Object sendDateValue = result.getProperty(Notification.JSON_PROPERTIES.sendDate.toString());
            Date sendDate = new ISO8601Date(sendDateValue.toString()).getTime();
            String advisorID = PathUtils.getAuthorizableId(result.getPath());
            LOGGER.debug("Found a queued notification at path " + result.getPath()
                    + ", send date " + sendDate + ", advisor ID " + advisorID);
            if (now.compareTo(sendDate) >= 0) {
                LOGGER.debug("The time has come to send notification at path " + result.getPath());
                sendNotification(result, contentManager);
            }
        }
    }

    private void sendNotification(Content result, ContentManager contentManager) {

        try {

            JSONObject json = new JSONObject((String) result.getProperty(Notification.JSON_PROPERTIES.calendarWrapper.toString()));
            CalendarWrapper wrapper = CalendarWrapper.fromJSON(json);

            JSONArray urisJson = new JSONArray();

            // save notification in bedework server
            // TODO get list of users based on dynamicListID and loop over them -- one connector per user
            CalDavConnector connector = this.calDavConnectorProvider.getCalDavConnector();
            wrapper.generateNewUID();
            CalendarURI uri = connector.putCalendar(wrapper.getCalendar(), "vbede");
            urisJson.put(uri.toJSON());

            // mark the notification as archived in our repo
            Content content = contentManager.get(result.getPath());
            content.setProperty(Notification.JSON_PROPERTIES.messageBox.toString(), Notification.MESSAGEBOX.archive.toString());
            content.setProperty(Notification.JSON_PROPERTIES.sendState.toString(), Notification.SEND_STATE.sent.toString());
            content.setProperty(Notification.JSON_PROPERTIES.calendarURIs.toString(), urisJson.toString());
            contentManager.update(content);

            LOGGER.info("Successfully sent notification; local path " + content.getPath() + "; bedework uri = " + uri);

        } catch (JSONException e) {
            LOGGER.error("Notification at path " + result.getPath() + " has invalid JSON for calendarWrapper", e);
        } catch (BadRequestException e) {
            LOGGER.error("Got bad request from CalDav server trying to post notification at path " + result.getPath(), e);
        } catch (IOException e) {
            LOGGER.error("Got exception trying to contact CalDav server to post notification at path " + result.getPath(), e);
        } catch (CalDavException e) {
            LOGGER.error("Notification at path " + result.getPath() + " has invalid calendar data", e);
        } catch (AccessDeniedException e) {
            LOGGER.error("Got access denied saving notification at path " + result.getPath(), e);
        } catch (StorageClientException e) {
            LOGGER.error("Got storage client exception saving notification at path " + result.getPath(), e);
        }
    }

}
