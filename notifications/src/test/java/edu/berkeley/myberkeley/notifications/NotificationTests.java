package edu.berkeley.myberkeley.notifications;

import org.junit.Assert;
import org.sakaiproject.nakamura.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public abstract class NotificationTests extends Assert {

    public String readNotificationFromFile() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("notification.json");
        return IOUtils.readFully(in, "utf-8");
    }

}
