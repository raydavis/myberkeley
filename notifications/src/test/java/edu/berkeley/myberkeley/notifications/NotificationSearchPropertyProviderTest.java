package edu.berkeley.myberkeley.notifications;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.util.HashMap;
import java.util.Map;

public class NotificationSearchPropertyProviderTest extends NotificationTests {

    private NotificationSearchPropertyProvider provider;

    @Before
    public void setup() {
        this.provider = new NotificationSearchPropertyProvider();
    }

    @Test
    public void testLoadUserProperties() throws Exception {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRemoteUser()).thenReturn("joe");
        Map<String, String> props = new HashMap<String, String>();

        provider.loadUserProperties(request, props);
        assertEquals(ClientUtils.escapeQueryChars(LitePersonalUtils.PATH_AUTHORIZABLE + "joe/" +
                Notification.STORE_NAME), props.get(Notification.SEARCH_PROP_NOTIFICATIONSTORE));

    }
}
