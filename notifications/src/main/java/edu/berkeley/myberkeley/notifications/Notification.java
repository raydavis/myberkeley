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
import org.sakaiproject.nakamura.util.ISO8601Date;

import java.util.UUID;

public abstract class Notification {

  public static final String RESOURCETYPE = "myberkeley/notification";
  public static final String STORE_NAME = "_myberkeley_notificationstore";
  public static final String STORE_RESOURCETYPE = "myberkeley/notificationstore";
  public static final String SEARCH_PROP_NOTIFICATIONSTORE = "_userNotificationPath";

  @SuppressWarnings({"UnusedDeclaration"})
  public enum TYPE {
    calendar,
    message
  }

  public enum SEND_STATE {
    pending,
    sent,
    error
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public enum MESSAGEBOX {
    drafts,
    queue,
    archive,
    trash
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public enum JSON_PROPERTIES {
    type,
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
    uxState,
    errMsg
  }

  protected TYPE type;

  private UUID id;

  private String senderID;

  private ISO8601Date sendDate;

  private SEND_STATE sendState;

  private MESSAGEBOX messageBox;

  private String dynamicListID;

  private JSONObject uxState;

  protected Notification(JSONObject json) throws JSONException, CalDavException {
    this.id = getNotificationID(json);
    this.senderID = json.getString(JSON_PROPERTIES.senderID.toString());
    this.sendDate = new ISO8601Date(json.getString(JSON_PROPERTIES.sendDate.toString()));
    this.dynamicListID = json.getString(JSON_PROPERTIES.dynamicListID.toString());

    // set defaults for optional properties
    SEND_STATE sendState = SEND_STATE.pending;
    MESSAGEBOX messageBox = MESSAGEBOX.drafts;
    JSONObject uxState = new JSONObject();

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
    this.sendState = sendState;
    this.messageBox = messageBox;
    this.uxState = uxState;
  }

  protected Notification(Content content) throws JSONException, CalDavException {
    this.id = UUID.fromString((String) content.getProperty(JSON_PROPERTIES.id.toString()));
    this.senderID = (String) content.getProperty(JSON_PROPERTIES.senderID.toString());
    this.sendDate = new ISO8601Date((String) content.getProperty(JSON_PROPERTIES.sendDate.toString()));
    this.dynamicListID = (String) content.getProperty(JSON_PROPERTIES.dynamicListID.toString());

    // set defaults for optional properties
    JSONObject uxState = new JSONObject();
    // set optional properties
    try {
      uxState = new JSONObject((String) content.getProperty(JSON_PROPERTIES.uxState.toString()));
    } catch (JSONException ignored) {
    }
    this.sendState = SEND_STATE.valueOf((String) content.getProperty(JSON_PROPERTIES.sendState.toString()));
    this.messageBox = MESSAGEBOX.valueOf((String) content.getProperty(JSON_PROPERTIES.messageBox.toString()));
    this.uxState = uxState;
  }

  public UUID getId() {
    return this.id;
  }

  public String getSenderID() {
    return this.senderID;
  }

  public ISO8601Date getSendDate() {
    return this.sendDate;
  }

  public SEND_STATE getSendState() {
    return this.sendState;
  }

  public MESSAGEBOX getMessageBox() {
    return this.messageBox;
  }

  public String getDynamicListID() {
    return this.dynamicListID;
  }

  public JSONObject getUXState() {
    return this.uxState;
  }

  public void toContent(String storePath, Content content) throws JSONException {
    content.setProperty("sakai:messagestore", storePath);
    content.setProperty(JSON_PROPERTIES.type.toString(), this.type.toString());
    content.setProperty(JSON_PROPERTIES.id.toString(), this.getId().toString());
    content.setProperty(JSON_PROPERTIES.senderID.toString(), this.getSenderID());
    content.setProperty(JSON_PROPERTIES.sendDate.toString(), this.getSendDate().toString());
    content.setProperty(JSON_PROPERTIES.sendState.toString(), this.getSendState().toString());
    content.setProperty(JSON_PROPERTIES.messageBox.toString(), this.getMessageBox().toString());
    content.setProperty(JSON_PROPERTIES.dynamicListID.toString(), this.getDynamicListID());
    content.setProperty(JSON_PROPERTIES.uxState.toString(), this.getUXState().toString());
  }

  static UUID getNotificationID(JSONObject notificationJSON) {
    try {
      return UUID.fromString(notificationJSON.getString(Notification.JSON_PROPERTIES.id.toString()));
    } catch (JSONException ignored) {
      // that's ok, we'll use the random UUID
      return UUID.randomUUID();
    }
  }

  @SuppressWarnings({"RedundantIfStatement"})
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || o.getClass().isAssignableFrom(Notification.class)) {
      return false;
    }

    Notification that = (Notification) o;

    if (this.dynamicListID != null ? !this.dynamicListID.equals(that.dynamicListID) : that.dynamicListID != null)
      return false;
    if (this.id != null ? !this.id.equals(that.id) : that.id != null) return false;
    if (this.senderID != null ? !this.senderID.equals(that.senderID) : that.senderID != null) return false;
    if (this.messageBox != that.messageBox) return false;
    if (this.sendDate != null ? !this.sendDate.equals(that.sendDate) : that.sendDate != null) return false;
    if (this.sendState != that.sendState) return false;
    if (this.uxState != null ? !this.uxState.toString().equals(that.uxState.toString()) : that.uxState != null)
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = this.id != null ? this.id.hashCode() : 0;
    result = 31 * result + (this.senderID != null ? this.senderID.hashCode() : 0);
    result = 31 * result + (this.sendDate != null ? this.sendDate.hashCode() : 0);
    result = 31 * result + (this.sendState != null ? this.sendState.hashCode() : 0);
    result = 31 * result + (this.messageBox != null ? this.messageBox.hashCode() : 0);
    result = 31 * result + (this.dynamicListID != null ? this.dynamicListID.hashCode() : 0);
    result = 31 * result + (this.uxState != null ? this.uxState.hashCode() : 0);
    return result;
  }
}
