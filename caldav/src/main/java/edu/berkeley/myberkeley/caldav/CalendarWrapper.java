package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.URI;

import java.text.ParseException;

public class CalendarWrapper {

    private Calendar calendar;

    private URI uri;

    private Date etag;

    public CalendarWrapper(Calendar calendar, URI uri, String etag) throws ParseException {
        this.calendar = calendar;
        this.uri = uri;
        this.etag = new DateTime(etag.replaceAll("\"", ""), "yyyyMMdd'T'HHmmss", true);
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public URI getUri() {
        return uri;
    }

    public Date getEtag() {
        return etag;
    }

    @Override
    public String toString() {
        return "CalendarWrapper{" +
                "uri='" + uri + '\'' +
                "etag=" + etag +
                ",calendar=" + calendar +
                '}';
    }
}
