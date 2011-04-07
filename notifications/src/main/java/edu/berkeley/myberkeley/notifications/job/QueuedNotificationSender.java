package edu.berkeley.myberkeley.notifications.job;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(label = "MyBerkeley :: QueuedNotificationSender",
        description = "Sends queued notifications when they reach their send date",
        immediate = true, metatype = true)
@Service(value = edu.berkeley.myberkeley.notifications.job.QueuedNotificationSender.class)
public class QueuedNotificationSender {

    private final Logger LOGGER = LoggerFactory.getLogger(QueuedNotificationSender.class);

    @Reference
    private Scheduler scheduler;

    @org.apache.felix.scr.annotations.Property(longValue = 60, label = "Poll Interval Seconds")
    private static final String PROP_POLL_INTERVAL_SECONDS = "queuedsender.pollinterval";

    private final static String JOB_NAME = "sendNotificationsJob";

    protected void activate(ComponentContext componentContext) throws Exception {
        Dictionary<?, ?> props = componentContext.getProperties();
        Long pollInterval = (Long) props.get(PROP_POLL_INTERVAL_SECONDS);
        Map<String, Serializable> config = new HashMap<String, Serializable>();
        final Job sendQueuedNoticeJob = new SendNotificationsJob();
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

}
