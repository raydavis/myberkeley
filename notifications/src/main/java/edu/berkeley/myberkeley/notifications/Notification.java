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
    calendarURIs
  }

  private UUID id;

  private ISO8601Date sendDate;

  private SEND_STATE sendState;

  private MESSAGEBOX messageBox;

  private String dynamicListID;

  private CalendarWrapper wrapper;

  private CATEGORY category;

  private JSONObject uxState;

  public Notification(JSONObject json) throws JSONException, CalDavException {
    this.id = getNotificationID(json);
    this.wrapper = CalendarWrapper.fromJSON(json.getJSONObject(JSON_PROPERTIES.calendarWrapper.toString()));
    this.sendDate = new ISO8601Date(json.getString(JSON_PROPERTIES.sendDate.toString()));
    this.dynamicListID = json.getString(JSON_PROPERTIES.dynamicListID.toString());
    this.category = CATEGORY.valueOf(json.getString(JSON_PROPERTIES.category.toString()));

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

  public UUID getId() {
    return id;
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

  public void toContent(String storePath, Content content) throws JSONException {
    content.setProperty("sakai:messagestore", storePath);
    content.setProperty(JSON_PROPERTIES.id.toString(), this.getId().toString());
    content.setProperty(JSON_PROPERTIES.sendDate.toString(), this.getSendDate().toString());
    content.setProperty(JSON_PROPERTIES.sendState.toString(), this.getSendState().toString());
    content.setProperty(JSON_PROPERTIES.messageBox.toString(), this.getMessageBox().toString());
    content.setProperty(JSON_PROPERTIES.dynamicListID.toString(), this.getDynamicListID());
    content.setProperty(JSON_PROPERTIES.calendarWrapper.toString(), this.getWrapper().toJSON().toString());
    content.setProperty(JSON_PROPERTIES.category.toString(), this.getCategory().toString());
    content.setProperty(JSON_PROPERTIES.uxState.toString(), this.getUXState().toString());
  }

  private static UUID getNotificationID(JSONObject notificationJSON) {
    try {
      return UUID.fromString(notificationJSON.getString(Notification.JSON_PROPERTIES.id.toString()));
    } catch (JSONException ignored) {
      // that's ok, we'll use the random UUID
      return UUID.randomUUID();
    }
  }

}
