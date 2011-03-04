package edu.berkeley.myberkeley.caldav.report;

import java.util.Date;

/**
 * <!ELEMENT time-range EMPTY>
 * <!ATTLIST time-range start CDATA #IMPLIED
 *                      end   CDATA #IMPLIED>
 *
 * Although it is IMPLIED instead of REQUIRED it will be used as
 * start and end were compulsory.
 *
 * @author ricky
 */
public class TimeRange extends StartEndRequiredData {

    public TimeRange(Date start, Date end) {
        super(CalDavConstants.CALDAV_XML_TIME_RANGE, start, end);
    }

}
