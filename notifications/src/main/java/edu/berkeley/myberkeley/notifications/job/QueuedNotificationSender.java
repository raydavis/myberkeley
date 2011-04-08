package edu.berkeley.myberkeley.notifications.job;

import edu.berkeley.myberkeley.notifications.Notification;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(label = "MyBerkeley :: QueuedNotificationSender",
        description = "Scheduled job that sends queued notifications when their send date has arrived",
        immediate = true, metatype = true)
@Service(value = edu.berkeley.myberkeley.notifications.job.QueuedNotificationSender.class)
public class QueuedNotificationSender {

    private final Logger LOGGER = LoggerFactory.getLogger(QueuedNotificationSender.class);

    @Reference
    protected Repository repository;

    @Reference
    protected Scheduler scheduler;

    @org.apache.felix.scr.annotations.Property(longValue = 60, label = "Poll Interval Seconds")
    protected static final String PROP_POLL_INTERVAL_SECONDS = "queuedsender.pollinterval";

    protected final static String JOB_NAME = "sendNotificationsJob";

    protected void activate(ComponentContext componentContext) throws Exception {
        Dictionary<?, ?> props = componentContext.getProperties();
        Long pollInterval = (Long) props.get(PROP_POLL_INTERVAL_SECONDS);
        Map<String, Serializable> config = new HashMap<String, Serializable>();
        final Job sendQueuedNoticeJob = getJob();
        try {
            LOGGER.debug("Activating SendNotificationsJob...");
            this.scheduler.addPeriodicJob(JOB_NAME, sendQueuedNoticeJob, config, pollInterval, false);
        } catch (Exception e) {
            LOGGER.error("Failed to add periodic job for QueuedNotificationSender", e);
        }
    }

    protected void deactivate(ComponentContext componentContext) throws Exception {
        LOGGER.debug("Removing SendNotificationsJob...");
        this.scheduler.removeJob(JOB_NAME);
    }

    protected final SendNotificationsJob getJob() {
        return new SendNotificationsJob();
    }

    protected class SendNotificationsJob implements Job {

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
                    LOGGER.info("Found a queued notification at path {}", result.getPath());
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

            /* props for a ContentMgr.find() :
           {sakai:messagebox=queue, resourceType=myberkeley/notification}

            */
        }

    }

}
