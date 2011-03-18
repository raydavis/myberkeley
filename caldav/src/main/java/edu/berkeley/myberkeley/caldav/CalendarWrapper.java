package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;

import java.text.ParseException;

public class CalendarWrapper {

    private Calendar calendar;

    private CalendarUri calendarUri;

    public CalendarWrapper(Calendar calendar, URI uri, String etag) throws CalDavException {
        this.calendar = calendar;
        try {
            this.calendarUri = new CalendarUri(uri,  etag);
        } catch ( ParseException pe ) {
            throw new CalDavException("Exception parsing date '" + etag + "'", pe);
        } catch ( URIException uie ) {
            throw new CalDavException("Exception parsing uri '" + uri + "'", uie);
        }
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public CalendarUri getUri() {
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

    public static class CalendarUri extends URI {

        private Date etag;

        public CalendarUri(URI uri, String etag) throws URIException, ParseException {
            super(uri.toString(), false);
            this.etag = new DateTime(etag.replaceAll("\"", ""), "yyyyMMdd'T'HHmmss", true);
        }

        public Date getEtag() {
            return etag;
        }

    }
}
