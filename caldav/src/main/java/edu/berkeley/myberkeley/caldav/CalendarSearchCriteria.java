package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;

public class CalendarSearchCriteria {

    public enum MODE {
        REQUIRED,
        UNREQUIRED,
        ALL_UNARCHIVED,
        ALL_ARCHIVED
    }

    public enum TYPE {
        VEVENT,
        VTODO
    }

    private TYPE type;

    private MODE mode;

    private Date start;

    private Date end;

    public CalendarSearchCriteria(TYPE type, Date start, Date end, MODE mode) {
        this.type = type;
        this.start = start;
        this.end = end;
        this.mode = mode;
    }

    public TYPE getType() {
        return type;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(Date end) {
        this.end = end;
    }

    public MODE getMode() {
        return mode;
    }

    public void setMode(MODE mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return "CalendarSearchCriteria{" +
                "type=" + type +
                ", mode=" + mode +
                ", start=" + start +
                ", end=" + end +
                '}';
    }

}

