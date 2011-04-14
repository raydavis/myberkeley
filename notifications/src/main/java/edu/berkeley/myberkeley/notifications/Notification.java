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

import edu.berkeley.myberkeley.caldav.CalDavException;
import edu.berkeley.myberkeley.caldav.CalendarWrapper;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.util.ISO8601Date;

import java.util.UUID;

public class Notification {

  public static final String RESOURCETYPE = "myberkeley/notification";
  public static final String STORE_NAME = "_myberkeley_notificationstore";
  public static final String STORE_RESOURCETYPE = "myberkeley/notificationstore";
  public static final String SEARCH_PROP_NOTIFICATIONSTORE = "_userNotificationPath";


  public enum SEND_STATE {
    pending,
    sent
  }

  public enum MESSAGEBOX {
    drafts,
    queue,
    archive,
    trash
  }

  public enum CATEGORY {
    reminder,           // a task or event stored in Bedework
    message             // a Sakai message (possibly also emailed out)
  }

  public enum JSON_PROPERTIES {
    id,
    senderID,
    sendDate,
    sendState,
    messageBox {
      @Override
      public String toString() {
        return "sakai:messagebox";
      }
    },
    dynamicListID,
    calendarWrapper,
    category,
    uxState,
    calendarURIs,
    emailMessageID
  }

  private UUID id;

  private String senderID;

  private ISO8601Date sendDate;

  private SEND_STATE sendState;

  private MESSAGEBOX messageBox;

  private String dynamicListID;

  private CalendarWrapper wrapper;

  private CATEGORY category;

  private JSONObject uxState;

  private String emailMessageID;

  public Notification(JSONObject json) throws JSONException, CalDavException {
    this.id = getNotificationID(json);
    this.senderID = json.getString(JSON_PROPERTIES.senderID.toString());
    this.wrapper = CalendarWrapper.fromJSON(json.getJSONObject(JSON_PROPERTIES.calendarWrapper.toString()));
    this.sendDate = new ISO8601Date(json.getString(JSON_PROPERTIES.sendDate.toString()));
    this.dynamicListID = json.getString(JSON_PROPERTIES.dynamicListID.toString());
    this.category = CATEGORY.valueOf(json.getString(JSON_PROPERTIES.category.toString()));

    // set defaults for optional properties
    SEND_STATE sendState = SEND_STATE.pending;
    MESSAGEBOX messageBox = MESSAGEBOX.drafts;
    JSONObject uxState = new JSONObject();
    String emailMessageID = null;

    // set optional properties
    try {
      uxState = json.getJSONObject(JSON_PROPERTIES.uxState.toString());
    } catch (JSONException ignored) {
    }
    try {
      sendState = SEND_STATE.valueOf(json.getString(JSON_PROPERTIES.sendState.toString()));
    } catch (JSONException ignored) {
    }
    try {
      messageBox = MESSAGEBOX.valueOf(json.getString(JSON_PROPERTIES.messageBox.toString()));
    } catch (JSONException ignored) {
    }
    try {
      emailMessageID = json.getString(JSON_PROPERTIES.emailMessageID.toString());
    } catch ( JSONException ignored ) {

    }
    this.sendState = sendState;
    this.messageBox = messageBox;
    this.uxState = uxState;
    this.emailMessageID = emailMessageID;
  }

  public Notification(Content content) throws JSONException, CalDavException {
    this.id = UUID.fromString((String) content.getProperty(JSON_PROPERTIES.id.toString()));
    this.senderID = (String) content.getProperty(JSON_PROPERTIES.senderID.toString());
    this.wrapper = CalendarWrapper.fromJSON(new JSONObject((String) content.getProperty(JSON_PROPERTIES.calendarWrapper.toString())));
    this.sendDate = new ISO8601Date((String) content.getProperty(JSON_PROPERTIES.sendDate.toString()));
    this.dynamicListID = (String) content.getProperty(JSON_PROPERTIES.dynamicListID.toString());
    this.category = CATEGORY.valueOf((String) content.getProperty(JSON_PROPERTIES.category.toString()));

    // set defaults for optional properties
    JSONObject uxState = new JSONObject();
    // set optional properties
    try {
      uxState = new JSONObject((String) content.getProperty(JSON_PROPERTIES.uxState.toString()));
    } catch (JSONException ignored) {
    }
    if ( content.hasProperty(JSON_PROPERTIES.emailMessageID.toString())) {
      this.emailMessageID = (String) content.getProperty(JSON_PROPERTIES.emailMessageID.toString());
    }
    this.sendState = SEND_STATE.valueOf((String) content.getProperty(JSON_PROPERTIES.sendState.toString()));
    this.messageBox = MESSAGEBOX.valueOf((String) content.getProperty(JSON_PROPERTIES.messageBox.toString()));
    this.uxState = uxState;
  }

  public UUID getId() {
    return id;
  }

  public String getSenderID() {
    return senderID;
  }

  public ISO8601Date getSendDate() {
    return sendDate;
  }

  public SEND_STATE getSendState() {
    return sendState;
  }

  public MESSAGEBOX getMessageBox() {
    return messageBox;
  }

  public String getDynamicListID() {
    return dynamicListID;
  }

  public CalendarWrapper getWrapper() {
    return wrapper;
  }

  public CATEGORY getCategory() {
    return category;
  }

  public JSONObject getUXState() {
    return uxState;
  }

  public String getEmailMessageID() {
    return emailMessageID;
  }

  public void toContent(String storePath, Content content) throws JSONException {
    content.setProperty("sakai:messagestore", storePath);
    content.setProperty(JSON_PROPERTIES.id.toString(), this.getId().toString());
    content.setProperty(JSON_PROPERTIES.senderID.toString(), this.getSenderID());
    content.setProperty(JSON_PROPERTIES.sendDate.toString(), this.getSendDate().toString());
    content.setProperty(JSON_PROPERTIES.sendState.toString(), this.getSendState().toString());
    content.setProperty(JSON_PROPERTIES.messageBox.toString(), this.getMessageBox().toString());
    content.setProperty(JSON_PROPERTIES.dynamicListID.toString(), this.getDynamicListID());
    content.setProperty(JSON_PROPERTIES.calendarWrapper.toString(), this.getWrapper().toJSON().toString());
    content.setProperty(JSON_PROPERTIES.category.toString(), this.getCategory().toString());
    content.setProperty(JSON_PROPERTIES.uxState.toString(), this.getUXState().toString());
    if ( this.getEmailMessageID() != null ) {
      content.setProperty(JSON_PROPERTIES.emailMessageID.toString(), this.getEmailMessageID());
    }
  }

  private static UUID getNotificationID(JSONObject notificationJSON) {
    try {
      return UUID.fromString(notificationJSON.getString(Notification.JSON_PROPERTIES.id.toString()));
    } catch (JSONException ignored) {
      // that's ok, we'll use the random UUID
      return UUID.randomUUID();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Notification that = (Notification) o;

    if (category != that.category) return false;
    if (dynamicListID != null ? !dynamicListID.equals(that.dynamicListID) : that.dynamicListID != null) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (senderID != null ? !senderID.equals(that.senderID) : that.senderID != null) return false;
    if (messageBox != that.messageBox) return false;
    if (sendDate != null ? !sendDate.equals(that.sendDate) : that.sendDate != null) return false;
    if (sendState != that.sendState) return false;
    if (wrapper != null ? !wrapper.equals(that.wrapper) : that.wrapper != null) return false;
    if (uxState != null ? !uxState.toString().equals(that.uxState.toString()) : that.uxState != null) return false;
    if (emailMessageID != null ? !emailMessageID.equals(that.emailMessageID) : that.emailMessageID != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (senderID != null ? senderID.hashCode() : 0);
    result = 31 * result + (sendDate != null ? sendDate.hashCode() : 0);
    result = 31 * result + (sendState != null ? sendState.hashCode() : 0);
    result = 31 * result + (messageBox != null ? messageBox.hashCode() : 0);
    result = 31 * result + (dynamicListID != null ? dynamicListID.hashCode() : 0);
    result = 31 * result + (wrapper != null ? wrapper.hashCode() : 0);
    result = 31 * result + (category != null ? category.hashCode() : 0);
    result = 31 * result + (uxState != null ? uxState.hashCode() : 0);
    result = 31 * result + (emailMessageID != null ? emailMessageID.hashCode() : 0);
    return result;
  }
}
