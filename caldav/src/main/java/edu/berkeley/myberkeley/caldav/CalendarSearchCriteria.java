package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.report.CalDavConstants;
import net.fortuna.ical4j.model.Date;

public class CalendarSearchCriteria {

    private CalDavConstants.COMPONENT component;

    private Date start;

    private Date end;

    private MODE mode;

    public CalendarSearchCriteria(CalDavConstants.COMPONENT component, Date start, Date end, MODE mode) {
        this.component = component;
        this.start = start;
        this.end = end;
        this.mode = mode;
    }

    public CalDavConstants.COMPONENT getComponent() {
        return component;
    }

    public void setComponent(CalDavConstants.COMPONENT component) {
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

    public enum MODE {
        REQUIRED,
        UNREQUIRED,
        ALL_UNARCHIVED,
        ALL_ARCHIVED
    }

}

