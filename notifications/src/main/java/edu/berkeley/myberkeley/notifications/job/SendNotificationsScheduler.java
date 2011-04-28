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
import edu.berkeley.myberkeley.caldav.api.CalDavConnectorProvider;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(label = "MyBerkeley :: SendNotificationsScheduler",
        description = "Scheduled job that sends queued notifications when their send date has arrived",
        immediate = true, metatype = true)
@Service(value = SendNotificationsScheduler.class)
public class SendNotificationsScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsScheduler.class);

  @Reference
  protected Repository sparseRepository;

  @Reference
  protected SlingRepository slingRepository;

  @Reference
  protected Scheduler scheduler;

  @Reference
  protected NotificationEmailSender emailSender;

  @Reference
  protected CalDavConnectorProvider provider;

  @Reference
  protected DynamicListService dynamicListService;

  @org.apache.felix.scr.annotations.Property(longValue = 60, label = "Poll Interval Seconds",
          description = "How often to wake up and check for outgoing notifications")
  protected static final String PROP_POLL_INTERVAL_SECONDS = "queuedsender.pollinterval";

  protected final static String JOB_NAME = "sendNotificationsJob";

  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    Long pollInterval = (Long) props.get(PROP_POLL_INTERVAL_SECONDS);
    Map<String, Serializable> config = new HashMap<String, Serializable>();
    final Job sendQueuedNoticeJob = new SendNotificationsJob(this.sparseRepository, this.slingRepository, this.emailSender, this.provider,
            this.dynamicListService);
    try {
      LOGGER.debug("Activating SendNotificationsJob...");
      this.scheduler.addPeriodicJob(JOB_NAME, sendQueuedNoticeJob, config, pollInterval, false);
    } catch (Exception e) {
      LOGGER.error("Failed to add periodic job for SendNotificationsScheduler", e);
    }
  }

  @SuppressWarnings({"UnusedParameters"})
  protected void deactivate(ComponentContext componentContext) throws Exception {
    LOGGER.debug("Removing SendNotificationsJob...");
    this.scheduler.removeJob(JOB_NAME);
  }

}
