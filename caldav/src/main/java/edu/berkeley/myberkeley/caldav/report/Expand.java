package edu.berkeley.myberkeley.caldav.report;

import java.util.Date;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * <!ELEMENT expand EMPTY>
 * <!ATTLIST expand start CDATA #REQUIRED
 * end   CDATA #REQUIRED>
 *
 * @author ricky
 */
public class Expand extends StartEndRequiredData {

  public Expand(Date start, Date end) {
    super(CalDavConstants.CALDAV_XML_EXPAND, start, end);
  }


}
