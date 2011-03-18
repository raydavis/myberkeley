package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;

public class CalendarSearchCriteria {

    public enum MODE {
        REQUIRED,
        UNREQUIRED,
        ALL_UNARCHIVED,
        ALL_ARCHIVED
    }

    public enum COMPONENT {
        VEVENT,
        VTODO
    }

    private COMPONENT component;

    private MODE mode;

    private Date start;

    private Date end;

    public CalendarSearchCriteria(COMPONENT component, Date start, Date end, MODE mode) {
        this.component = component;
        this.start = start;
        this.end = end;
        this.mode = mode;
    }

    public COMPONENT getComponent() {
        return component;
    }

    public void setComponent(COMPONENT component) {
        this.component = component;
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
                "component=" + component +
                ", mode=" + mode +
                ", start=" + start +
                ", end=" + end +
                '}';
    }

}

