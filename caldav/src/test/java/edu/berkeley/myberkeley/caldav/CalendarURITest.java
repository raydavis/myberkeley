package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Test;

public class CalendarURITest extends CalDavTests {

    @Test
    public void toJSONAndBackAgain() throws URIException, JSONException {
        CalendarURI uri = new CalendarURI(new URI("/foo", false), new Date());
        JSONObject json = uri.toJSON();
        CalendarURI deserialized = new CalendarURI(json);
        assertEquals(uri, deserialized);
        assertEquals(uri.getEtag(), deserialized.getEtag());
    }

}
