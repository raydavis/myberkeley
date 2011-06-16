package edu.berkeley.myberkeley.notifications;

import edu.berkeley.myberkeley.caldav.api.CalDavException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

public class NotificationFactory {

  public static Notification getFromJSON(JSONObject json) throws JSONException, CalDavException {
    Notification.TYPE type = Notification.TYPE.valueOf(json.getString(
            Notification.JSON_PROPERTIES.type.toString()));

    if ( Notification.TYPE.calendar.equals(type)) {
      return new CalendarNotification(json);
    }

    throw new IllegalArgumentException("Passed json did not specify a supported notification type");
  }


}
