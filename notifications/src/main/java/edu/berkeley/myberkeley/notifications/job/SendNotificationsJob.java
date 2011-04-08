package edu.berkeley.myberkeley.notifications.job;

import edu.berkeley.myberkeley.notifications.Notification;
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SendNotificationsJob implements Job {

    private final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsJob.class);

    final Repository repository;

    public SendNotificationsJob(Repository repository) {
        this.repository = repository;
    }

    public void execute(JobContext jobContext) {
        long startMillis = System.currentTimeMillis();

        Session adminSession = null;

        try {
            adminSession = repository.loginAdministrative();
            Iterable<Content> results = getQueuedNotifications(adminSession);
            processResults(results);
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

    private void processResults(Iterable<Content> results) {
        Date now = new Date();
        for (Content result : results) {
            Object sendDateValue = result.getProperty(Notification.JSON_PROPERTIES.sendDate.toString());
            Date sendDate = new ISO8601Date(sendDateValue.toString()).getTime();
            String advisorID = PathUtils.getAuthorizableId(result.getPath());
            LOGGER.info("Found a queued notification at path " + result.getPath()
                    + ", send date " + sendDate + ", advisor ID " + advisorID);
            if (now.compareTo(sendDate) >= 0) {
                LOGGER.info("The time has come to send notification at path " + result.getPath());
            }
        }
    }

}
