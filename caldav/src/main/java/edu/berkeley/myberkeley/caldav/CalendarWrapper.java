package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;

public class CalendarWrapper {

    private Calendar calendar;

    private String uri;

    public CalendarWrapper(Calendar calendar, String uri) {
        this.calendar = calendar;
        this.uri = uri;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public String getUri() {
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
