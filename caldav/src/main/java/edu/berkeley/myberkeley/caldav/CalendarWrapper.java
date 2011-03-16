package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import org.apache.commons.httpclient.URI;

public class CalendarWrapper {

    private Calendar calendar;

    private URI uri;

    public CalendarWrapper(Calendar calendar, URI uri) {
        this.calendar = calendar;
        this.uri = uri;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public URI getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return "CalendarWrapper{" +
                "uri='" + uri + '\'' +
                ",calendar=" + calendar +
                '}';
    }
}
