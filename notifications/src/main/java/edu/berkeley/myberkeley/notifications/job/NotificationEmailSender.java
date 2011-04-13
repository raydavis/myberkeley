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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;

@Component(label = "MyBerkeley :: NotificationEmailSender",
        description = "Sends emails for notifications",
        immediate = true, metatype = true)
@Service(value = NotificationEmailSender.class)
public class NotificationEmailSender {

  @Property(value = "localhost")
  static final String SMTP_SERVER = "sakai.smtp.server";
  @Property(intValue = 25, label = "%sakai.smtp.port.name")
  static final String SMTP_PORT = "sakai.smtp.port";
  @Property(intValue = 240)
  static final String MAX_RETRIES = "sakai.email.maxRetries";
  @Property(intValue = 30)
  static final String RETRY_INTERVAL = "sakai.email.retryIntervalMinutes";
  @Property(boolValue = false, label = "%sakai.email.sendemail.name", description = "%sakai.email.sendemail.description")
  static final String SEND_EMAIL = "notifications.sendEmail";

  private Integer maxRetries;
  private Integer smtpPort;
  private String smtpServer;
  private Integer retryInterval;
  private boolean sendEmail;

  private final Logger LOGGER = LoggerFactory.getLogger(SendNotificationsScheduler.class);

  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    this.maxRetries = (Integer) props.get(MAX_RETRIES);
    this.retryInterval = (Integer) props.get(RETRY_INTERVAL);
    this.smtpPort = (Integer) props.get(SMTP_PORT);
    this.smtpServer = (String) props.get(SMTP_SERVER);
    this.sendEmail = (Boolean) props.get(SEND_EMAIL);
  }

  protected void deactivate(ComponentContext componentContext) throws Exception {
    // nothing to do
  }

}
