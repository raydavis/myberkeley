package edu.berkeley.myberkeley.caldav.report;

import java.util.Date;

/**
 * <!ELEMENT limit-freebusy-set EMPTY>
 * <!ATTLIST limit-freebusy-set start CDATA #REQUIRED
 *                              end   CDATA #REQUIRED>
 *
 * @author ricky
 */
public class LimitFreeBusySet extends StartEndRequiredData {

    public LimitFreeBusySet(Date start, Date end) {
        super(CalDavConstants.CALDAV_XML_LIMIT_FREEBUSY_SET, start, end);
    }

}
