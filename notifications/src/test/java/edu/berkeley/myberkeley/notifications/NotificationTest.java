package edu.berkeley.myberkeley.notifications;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.caldav.CalDavException;
import edu.berkeley.myberkeley.caldav.CalendarWrapper;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class NotificationTest extends Assert {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationTest.class);

    @Test
    public void fromJSON() throws IOException, JSONException, CalDavException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("notification.json");
        String json = IOUtils.readFully(in, "utf-8");
        Notification notification = Notification.fromJSON(new JSONObject(json));
        assertEquals(Notification.SEND_STATE.sent, notification.getSendState());
        assertEquals(Notification.MESSAGEBOX.drafts, notification.getMessageBox());
    }

    @Test
    public void toContent() throws IOException, JSONException, CalDavException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("notification.json");
        String json = IOUtils.readFully(in, "utf-8");
        Notification notification = Notification.fromJSON(new JSONObject(json));
        Content content = new Content("/some/path", ImmutableMap.of(
                JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                (Object) Notification.RESOURCETYPE));
        notification.toContent(content);
        LOGGER.info("Content after notification.toContent() call: {}", content.toString());
        assertEquals(content.getProperty(Notification.JSON_PROPERTIES.sendState.toString()), Notification.SEND_STATE.sent.toString());
        assertEquals(content.getProperty(Notification.JSON_PROPERTIES.messageBox.toString()), Notification.MESSAGEBOX.drafts.toString());
        CalendarWrapper wrapper = CalendarWrapper.fromJSON(new JSONObject((String)content.getProperty(Notification.JSON_PROPERTIES.calendarWrapper.toString())));
        assertNotNull(wrapper);
        assertTrue(wrapper.isRequired());
    }
}
