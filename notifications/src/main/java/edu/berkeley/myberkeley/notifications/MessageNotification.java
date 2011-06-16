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

package edu.berkeley.myberkeley.notifications;

import edu.berkeley.myberkeley.caldav.api.CalDavException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.content.Content;

public class MessageNotification extends Notification {

  public enum JSON_PROPERTIES {
    subject,
    body
  }

  private String subject;

  private String body;

  public MessageNotification(JSONObject json) throws JSONException, CalDavException {
    super(json);
    this.type = TYPE.message;
    this.subject = json.getString(JSON_PROPERTIES.subject.toString());
    this.body = json.getString(JSON_PROPERTIES.body.toString());
  }

  public MessageNotification(Content content) throws JSONException, CalDavException {
    super(content);
    this.type = TYPE.message;
    this.subject = (String) content.getProperty(JSON_PROPERTIES.subject.toString());
    this.body = (String) content.getProperty(JSON_PROPERTIES.body.toString());
  }

  @Override
  public void toContent(String storePath, Content content) throws JSONException {
    super.toContent(storePath, content);
    content.setProperty(JSON_PROPERTIES.subject.toString(), getSubject());
    content.setProperty(JSON_PROPERTIES.body.toString(), getBody());
  }

  public String getSubject() {
    return this.subject;
  }

  public String getBody() {
    return this.body;
  }
}
