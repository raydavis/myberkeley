package edu.berkeley.myberkeley.notifications;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.Writer;

public class NotificationSearchResultProcessorTest extends NotificationTests {

    private NotificationSearchResultProcessor processor;

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSearchResultProcessorTest.class);

    @Before
    public void setup() {
        this.processor = new NotificationSearchResultProcessor();
        processor.searchServiceFactory = mock(SolrSearchServiceFactory.class);
    }

    @Test
    public void testWriteResults() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        Session session = mock(Session.class);

        AccessControlManager accessControlManager = mock(AccessControlManager.class);
        when(session.getAccessControlManager()).thenReturn(accessControlManager);

        AuthorizableManager authMgr = mock(AuthorizableManager.class);
        when(session.getAuthorizableManager()).thenReturn(authMgr);

        ResourceResolver resolver = mock(ResourceResolver.class);
        when(request.getResourceResolver()).thenReturn(resolver);
        Object hybridSession = mock(javax.jcr.Session.class,
                withSettings().extraInterfaces(SessionAdaptable.class));
        when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(
                (javax.jcr.Session) hybridSession);
        when(((SessionAdaptable) hybridSession).getSession()).thenReturn(session);

        ContentManager cm = mock(ContentManager.class);
        when(session.getContentManager()).thenReturn(cm);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Writer w = new PrintWriter(baos);
        ExtendedJSONWriter writer = new ExtendedJSONWriter(w);

        Content contentA = new Content("/note/a", null);
        Notification notificationA = Notification.fromJSON(new JSONObject(readNotificationFromFile()));
        notificationA.toContent("/note", contentA);

        when(cm.get("/note/a")).thenReturn(contentA);

        processor.writeResult(request, writer, mockResult(contentA));
        w.flush();

        String s = baos.toString("UTF-8");

        JSONObject json = new JSONObject(s);
        LOGGER.info("JSON = " + json.toString(2));
    }

    private Result mockResult(Content content) {
        Result r = mock(Result.class);
        when(r.getPath()).thenReturn(content.getPath());
        return r;
    }

}
