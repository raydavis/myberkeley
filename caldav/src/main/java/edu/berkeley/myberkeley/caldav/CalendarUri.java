package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.URI;

import java.text.ParseException;

public class CalendarUri {

    private URI uri;

    private Date etag;

    public CalendarUri(URI uri, String etag) throws ParseException {
        this.uri = uri;
        this.etag = new DateTime(etag.replaceAll("\"", ""), "yyyyMMdd'T'HHmmss", true);
    }

    public URI getUri() {
        return uri;
    }

    public Date getEtag() {
        return etag;
    }

    @Override
    public String toString() {
        return "CalendarUri{" +
                "uri='" + uri + '\'' +
                ", etag=" + etag +
                '}';
    }
}
