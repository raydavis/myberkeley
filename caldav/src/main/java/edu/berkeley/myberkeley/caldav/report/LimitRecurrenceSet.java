package edu.berkeley.myberkeley.caldav.report;

import java.util.Date;

/**
 * <!ELEMENT limit-recurrence-set EMPTY>
 * <!ATTLIST limit-recurrence-set start CDATA #REQUIRED
 *                                end   CDATA #REQUIRED>
 *
 * @author ricky
 */
public class LimitRecurrenceSet extends StartEndRequiredData {

    public LimitRecurrenceSet(Date start, Date end) {
        super(CalDavConstants.CALDAV_XML_LIMIT_RECURRENCE_SET, start, end);
    }
    
}
