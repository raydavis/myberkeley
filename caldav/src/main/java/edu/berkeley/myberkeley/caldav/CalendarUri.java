package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;

import java.text.ParseException;

public class CalendarUri {

    private String uri;

    private Date etag;

    public CalendarUri(String uri, String etag) throws ParseException {
        this.uri = uri;
        this.etag = new DateTime(etag.replaceAll("\"", ""), "yyyyMMdd'T'HHmmss", true);
    }

    public String getUri() {
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
