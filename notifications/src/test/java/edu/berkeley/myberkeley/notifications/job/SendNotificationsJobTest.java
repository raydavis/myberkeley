package edu.berkeley.myberkeley.notifications.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.caldav.CalDavConnector;
import edu.berkeley.myberkeley.caldav.CalDavException;
import edu.berkeley.myberkeley.notifications.Notification;
import edu.berkeley.myberkeley.notifications.NotificationTests;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SendNotificationsJobTest extends NotificationTests {

    private SendNotificationsJob job;

    @Before
    public void setup() {
        Repository repo = mock(Repository.class);
        CalDavConnectorProvider provider = mock(CalDavConnectorProvider.class);
        this.job = new SendNotificationsJob(repo);
        this.job.calDavConnectorProvider = provider;
    }

    @Test
    public void execute() throws StorageClientException, AccessDeniedException, IOException, JSONException, CalDavException {
        JobContext context = mock(JobContext.class);

        Session adminSession = mock(Session.class);
        when(this.job.repository.loginAdministrative()).thenReturn(adminSession);

        ContentManager cm = mock(ContentManager.class);
        when(adminSession.getContentManager()).thenReturn(cm);

        Notification notification = new Notification(new JSONObject(readNotificationFromFile()));
        Content content = new Content("a:123456/_myberkeley_notificationstore/notice1", ImmutableMap.of(
                JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                (Object) Notification.RESOURCETYPE));
        notification.toContent("/notice1", content);
        List<Content> results = new ArrayList<Content>();
        results.add(content);
        when(cm.find(Matchers.anyMap())).thenReturn(results);

        when(cm.get("a:123456/_myberkeley_notificationstore/notice1")).thenReturn(content);

        CalDavConnector connector = mock(CalDavConnector.class);
        when(this.job.calDavConnectorProvider.getCalDavConnector()).thenReturn(connector);

        this.job.execute(context);
    }
}
