package edu.berkeley.myberkeley.notifications.job;

import edu.berkeley.myberkeley.caldav.CalDavConnector;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;

public class CalDavConnectorProvider {

    public CalDavConnector getCalDavConnector() throws URIException {
        // TODO get connector appropriate for recipient user
        // TODO make admin password configurable
        return new CalDavConnector("admin", "bedework",
                    new URI("http://test.media.berkeley.edu:8080", false),
                    new URI("http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/", false));
    }

}
