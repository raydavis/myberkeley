package edu.berkeley.myberkeley.caldav.report;

import java.util.Date;

/**
 * <!ELEMENT expand EMPTY>
 * <!ATTLIST expand start CDATA #REQUIRED
 *                  end   CDATA #REQUIRED>
 *
 * @author ricky
 */
public class Expand extends StartEndRequiredData {

    public Expand(Date start, Date end) {
        super(CalDavConstants.CALDAV_XML_EXPAND, start, end);
    }


}
