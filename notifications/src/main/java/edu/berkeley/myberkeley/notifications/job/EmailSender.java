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

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Component(label = "MyBerkeley :: EmailSender",
        description = "Sends emails for various notification purposes",
        immediate = true, metatype = true)
@Service(value = EmailSender.class)
public class EmailSender {

  @Property(value = "localhost", label = "SMTP Server")
  static final String SMTP_SERVER = "smtp.server";
  @Property(intValue = 25, label = "SMTP Port")
  static final String SMTP_PORT = "smtp.port";
  @Property(boolValue = false, label = "Send email?", description = "Whether or not to actually send real email")
  static final String SEND_EMAIL = "email.sendEmailEnabled";

  Integer smtpPort;
  String smtpServer;
  boolean sendEmail;

  @Reference
  ProfileService profileService;

  private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);

  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    this.smtpPort = OsgiUtil.toInteger(props.get(SMTP_PORT), 25);
    this.smtpServer = OsgiUtil.toString(props.get(SMTP_SERVER), "");
    this.sendEmail = OsgiUtil.toBoolean(props.get(SEND_EMAIL), false);
    LOGGER.info("EmailSender started up; sendEmail = " + this.sendEmail + "; smtpPort = "
            + this.smtpPort + "; smtpServer = " + this.smtpServer);
  }

  @SuppressWarnings({"UnusedParameters"})
  protected void deactivate(ComponentContext componentContext) throws Exception {
    // nothing to do
  }

  public void prepareMessage(MultiPartEmail email) throws EmailException {
    email.setDebug(true);
    email.setSmtpPort(this.smtpPort);
    email.setHostName(this.smtpServer);
    email.buildMimeMessage();
  }

  public String send(MultiPartEmail email)  {
    String messageID = null;
    logEmail(email.getMimeMessage());
    try {
      if (this.sendEmail) {
        messageID = email.sendMimeMessage();
        LOGGER.info("Sent real email with outgoing message ID = " + messageID);
      } else {
        messageID = "sendEmail is false, email not sent";
        LOGGER.info(messageID);
      }
    } catch (EmailException e) {
      LOGGER.error("EmailSender failed", e);
    }
    return messageID;
  }

  String userIDToEmail(String id, Session sparseSession, javax.jcr.Session jcrSession) throws StorageClientException, AccessDeniedException, RepositoryException {
    AuthorizableManager authMgr = sparseSession.getAuthorizableManager();
    Authorizable authz = authMgr.findAuthorizable(id);
    ValueMap profile = this.profileService.getProfileMap(authz, jcrSession);
    // note that we depend heavily on the profile ValueMap structure being correct. we find our email addresses
    // in the path /profile/email/elements/email/value . This code will break if/when we revise our profile structure.
    Map basic = (Map) profile.get("email");
    Map elements = (Map) basic.get("elements");
    Map email = (Map) elements.get("email");
    return (String) email.get("value");
  }


  private void logEmail(MimeMessage mimeMessage) {
    if ((!this.sendEmail || LOGGER.isDebugEnabled()) && mimeMessage != null) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mimeMessage.writeTo(new FilterOutputStream(baos));
        if (this.sendEmail) {
          LOGGER.debug("Email content = " + baos.toString());
        } else {
          LOGGER.info("Email content = " + baos.toString());
        }
      } catch (IOException e) {
        LOGGER.error("failed to log email", e);
      } catch (MessagingException e) {
        LOGGER.error("failed to log email", e);
      }
    }
  }

}

