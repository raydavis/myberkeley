package edu.berkeley.myberkeley.notifications.job;

import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendNotificationsJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsJob.class);

    public void execute(JobContext jobContext) {
        LOGGER.info("SendNotificationsJob executing...");
    }

}
