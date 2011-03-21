package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;

import java.text.ParseException;

public class CalendarWrapper {

    private Calendar calendar;

    private CalendarURI calendarUri;

    public CalendarWrapper(Calendar calendar, URI uri, String etag) throws CalDavException {
        this.calendar = calendar;
        try {
            this.calendarUri = new CalendarURI(uri,  etag);
        } catch ( ParseException pe ) {
            throw new CalDavException("Exception parsing date '" + etag + "'", pe);
        } catch ( URIException uie ) {
            throw new CalDavException("Exception parsing uri '" + uri + "'", uie);
        }
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public CalendarURI getUri() {
        return calendarUri;
    }

    public Date getEtag() {
        return this.calendarUri.getEtag();
    }

    @Override
    public String toString() {
        return "CalendarWrapper{" +
                "uri='" + getUri().toString() + '\'' +
                "etag=" + getUri().getEtag() +
                ",calendar=" + calendar +
                '}';
    }

}
