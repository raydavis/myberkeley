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

    public enum JSON_PROPERTIES {
        id,
        sendDate,
        dynamicListID,
        calendarWrapper
    }

    private String id;

    private ISO8601Date sendDate;

    private String dynamicListID;

    private CalendarWrapper wrapper;

    public Notification(String id, ISO8601Date sendDate, String dynamicListID, CalendarWrapper wrapper) {
        this.id = id;
        this.sendDate = sendDate;
        this.dynamicListID = dynamicListID;
        this.wrapper = wrapper;
    }

    public String getId() {
        return id;
    }

    public ISO8601Date getSendDate() {
        return sendDate;
    }

    public String getDynamicListID() {
        return dynamicListID;
    }

    public CalendarWrapper getWrapper() {
        return wrapper;
    }

    public void toContent(Content content) throws JSONException {
        // TODO see if we can store the wrapper as a CalendarWrapper object, not a String encoded JSON object.
        content.setProperty(JSON_PROPERTIES.id.toString(), this.getId());
        content.setProperty(JSON_PROPERTIES.sendDate.toString(), this.getSendDate().toString());
        content.setProperty(JSON_PROPERTIES.dynamicListID.toString(), this.getDynamicListID());
        content.setProperty(JSON_PROPERTIES.calendarWrapper.toString(), this.getWrapper().toJSON().toString());
    }

    public static Notification fromJSON(JSONObject json) throws JSONException, CalDavException {
        JSONObject calendarWrapperJSON = json.getJSONObject(JSON_PROPERTIES.calendarWrapper.toString());
        CalendarWrapper wrapper = CalendarWrapper.fromJSON(calendarWrapperJSON);
        ISO8601Date sendDate = new ISO8601Date(json.getString(JSON_PROPERTIES.sendDate.toString()));
        String dynamicListID = json.getString(JSON_PROPERTIES.dynamicListID.toString());
        return new Notification(getNotificationID(json), sendDate, dynamicListID, wrapper);
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
