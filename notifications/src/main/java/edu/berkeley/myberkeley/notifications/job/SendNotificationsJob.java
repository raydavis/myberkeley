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
            ContentManager cm = adminSession.getContentManager();
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("sakai:messagebox", Notification.MESSAGEBOX.queue.toString());
            props.put("sling:resourceType", Notification.RESOURCETYPE);
            Iterable<Content> results = cm.find(props);
            for (Content result : results) {
                Object sendDateValue = result.getProperty(Notification.JSON_PROPERTIES.sendDate.toString());
                ISO8601Date sendDate = new ISO8601Date(sendDateValue.toString());
                String advisorID = PathUtils.getAuthorizableId(result.getPath());
                LOGGER.info("Found a queued notification at path " + result.getPath()
                        + ", send date " + sendDate + ", advisor ID " + advisorID);
            }
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

}
