package edu.berkeley.myberkeley.caldav.report;

import java.util.Date;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * <p/>
 * <!ELEMENT time-range EMPTY>
 * <!ATTLIST time-range start CDATA #IMPLIED
 * end   CDATA #IMPLIED>
 * <p/>
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
