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
        uxState
    }

    private String id;

    private ISO8601Date sendDate;

    private SEND_STATE sendState;

    private MESSAGEBOX messageBox;

    private String dynamicListID;

    private CalendarWrapper wrapper;

    private String category;

    private JSONObject uxState;

    public Notification(String id, ISO8601Date sendDate, SEND_STATE sendState, MESSAGEBOX messageBox,
                        String dynamicListID, CalendarWrapper wrapper, String category, JSONObject uxState) {
        this.id = id;
        this.sendDate = sendDate;
        this.sendState = sendState;
        this.messageBox = messageBox;
        this.dynamicListID = dynamicListID;
        this.wrapper = wrapper;
        this.category = category;
        this.uxState = uxState;
    }

    public String getId() {
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

    public String getCategory() {
        return category;
    }

    public JSONObject getUXState() {
        return uxState;
    }

    public void toContent(Content content) throws JSONException {
        // TODO see if we can store the wrapper as a CalendarWrapper object, not a String encoded JSON object.
        content.setProperty(JSON_PROPERTIES.id.toString(), this.getId());
        content.setProperty(JSON_PROPERTIES.sendDate.toString(), this.getSendDate().toString());
        content.setProperty(JSON_PROPERTIES.sendState.toString(), this.getSendState().toString());
        content.setProperty(JSON_PROPERTIES.messageBox.toString(), this.getMessageBox().toString());
        content.setProperty(JSON_PROPERTIES.dynamicListID.toString(), this.getDynamicListID());
        content.setProperty(JSON_PROPERTIES.calendarWrapper.toString(), this.getWrapper().toJSON().toString());
        content.setProperty(JSON_PROPERTIES.category.toString(), this.getCategory());
        content.setProperty(JSON_PROPERTIES.uxState.toString(), this.getUXState().toString());
    }

    public static Notification fromJSON(JSONObject json) throws JSONException, CalDavException {
        JSONObject calendarWrapperJSON = json.getJSONObject(JSON_PROPERTIES.calendarWrapper.toString());
        CalendarWrapper wrapper = CalendarWrapper.fromJSON(calendarWrapperJSON);
        ISO8601Date sendDate = new ISO8601Date(json.getString(JSON_PROPERTIES.sendDate.toString()));
        String dynamicListID = json.getString(JSON_PROPERTIES.dynamicListID.toString());
        String category = json.getString(JSON_PROPERTIES.category.toString());
        SEND_STATE sendState = SEND_STATE.pending;
        MESSAGEBOX messagebox = MESSAGEBOX.drafts;
        JSONObject uxState = new JSONObject();
        try {
            uxState = json.getJSONObject(JSON_PROPERTIES.uxState.toString());
        } catch (JSONException ignored) {
        }
        try {
            sendState = SEND_STATE.valueOf(json.getString(JSON_PROPERTIES.sendState.toString()));
            messagebox = MESSAGEBOX.valueOf(json.getString(JSON_PROPERTIES.messageBox.toString()));
        } catch (JSONException ignored) {
            // those props are optional, it's ok if they're missing
        }
        return new Notification(getNotificationID(json), sendDate, sendState, messagebox, dynamicListID, wrapper, category, uxState);
    }

    private static String getNotificationID(JSONObject notificationJSON) {
        try {
            return notificationJSON.getString(Notification.JSON_PROPERTIES.id.toString());
        } catch (JSONException ignored) {
            // that's ok, we'll use the random UUID
            return UUID.randomUUID().toString();
        }
    }

}
