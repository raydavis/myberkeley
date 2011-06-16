package edu.berkeley.myberkeley.notifications;

import edu.berkeley.myberkeley.caldav.api.CalDavException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.content.Content;

public class NotificationFactory {

  public static Notification getFromJSON(JSONObject json) throws JSONException, CalDavException {
    Notification.TYPE type = Notification.TYPE.valueOf(json.getString(
            Notification.JSON_PROPERTIES.type.toString()));

    if (Notification.TYPE.calendar.equals(type)) {
      return new CalendarNotification(json);
    }
    if (Notification.TYPE.message.equals(type)) {
      return new MessageNotification(json);
    }
    throw new IllegalArgumentException("Passed json does not contain a supported notification type");
  }

  public static Notification getFromContent(Content content) throws JSONException, CalDavException {
    Notification.TYPE type = Notification.TYPE.valueOf((String) content.getProperty(
            Notification.JSON_PROPERTIES.type.toString()
    ));

    if (Notification.TYPE.calendar.equals(type)) {
      return new CalendarNotification(content);
    }
    if (Notification.TYPE.message.equals(type)) {
      return new MessageNotification(content);
    }
    throw new IllegalArgumentException("Passed content object does not contain a supported notification type");
  }

}
