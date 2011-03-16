package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;

import java.text.ParseException;

public class CalendarUri extends URI {

    private Date etag;

    public CalendarUri(URI uri, String etag) throws ParseException, URIException {
        super(uri.toString(), false);
        this.etag = new DateTime(etag.replaceAll("\"", ""), "yyyyMMdd'T'HHmmss", true);
    }

    public Date getEtag() {
        return etag;
    }

}
