package edu.berkeley.myberkeley.notifications.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.berkeley.myberkeley.notifications.NotificationTests;
import org.apache.sling.commons.scheduler.Scheduler;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.Repository;

import java.util.Dictionary;
import java.util.Hashtable;

public class SendNotificationsSchedulerTest extends NotificationTests {

    private SendNotificationsScheduler sender;

    @Before
    public void setup() {
        this.sender = new SendNotificationsScheduler();
        this.sender.repository = mock(Repository.class);
        this.sender.scheduler = mock(Scheduler.class);
    }

    @Test
    public void activate() throws Exception {
        ComponentContext context = mock(ComponentContext.class);
        Dictionary<String, Long> dictionary = new Hashtable<String, Long>();
        dictionary.put(SendNotificationsScheduler.PROP_POLL_INTERVAL_SECONDS, 60L);
        when(context.getProperties()).thenReturn(dictionary);
        this.sender.activate(context);
    }

    @Test
    public void deactivate() throws Exception {
        ComponentContext context = mock(ComponentContext.class);
        this.sender.deactivate(context);
    }

}
