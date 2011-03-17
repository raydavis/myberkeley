package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.report.CalDavConstants;
import net.fortuna.ical4j.model.Date;

public class CalendarSearchCriteria {

    private CalDavConstants.COMPONENT component;

    private Date start;

    private Date end;

    private Boolean required;

    private Boolean archive;

    public CalendarSearchCriteria(CalDavConstants.COMPONENT component, Date start, Date end, Boolean required, Boolean archive) {
        this.component = component;
        this.start = start;
        this.end = end;
        this.required = required;
        this.archive = archive;
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

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getArchive() {
        return archive;
    }

    public void setArchive(Boolean archive) {
        this.archive = archive;
    }
}

