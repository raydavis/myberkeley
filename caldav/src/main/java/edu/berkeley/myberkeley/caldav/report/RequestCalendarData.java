package edu.berkeley.myberkeley.caldav.report;

import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * <p/>
 * <!ELEMENT calendar-data (comp?,
 * (expand | limit-recurrence-set)?,
 * limit-freebusy-set?)>
 *
 * @author ricky
 */
public class RequestCalendarData implements XmlSerializable {

  private Comp comp = null;
  private Object expandOrLimitRecurrenceSet = null;
  private LimitFreeBusySet limitFreeBusySet = null;

  public Comp getComp() {
    return comp;
  }

  public void setComp(Comp comp) {
    this.comp = comp;
  }

  public Expand getExpand() {
    if (expandOrLimitRecurrenceSet instanceof Expand) {
      return (Expand) expandOrLimitRecurrenceSet;
    } else {
      return null;
    }
  }

  public void setExpand(Expand expand) {
    this.expandOrLimitRecurrenceSet = expand;
  }

  public LimitRecurrenceSet getLimitResourceSet() {
    if (expandOrLimitRecurrenceSet instanceof LimitRecurrenceSet) {
      return (LimitRecurrenceSet) expandOrLimitRecurrenceSet;
    } else {
      return null;
    }
  }

  public void setLimitResourceSet(LimitRecurrenceSet limitRecurrenceSet) {
    this.expandOrLimitRecurrenceSet = limitRecurrenceSet;
  }

  public LimitFreeBusySet getLimitFreeBusySet() {
    return limitFreeBusySet;
  }

  public void setLimitFreeBusySet(LimitFreeBusySet limitFreeBusySet) {
    this.limitFreeBusySet = limitFreeBusySet;
  }

  public Element toXml(Document factory) {
    Element e = DomUtil.createElement(factory,
            CalDavConstants.CALDAV_XML_CALENDAR_DATA, CalDavConstants.CALDAV_NAMESPACE);
    // comp
    if (comp != null) {
      e.appendChild(comp.toXml(factory));
    }
    // expandOrLimitRecurrenceSet
    if (expandOrLimitRecurrenceSet != null) {
      if (expandOrLimitRecurrenceSet instanceof Expand) {
        e.appendChild(((Expand) expandOrLimitRecurrenceSet).toXml(factory));
      } else if (expandOrLimitRecurrenceSet instanceof LimitRecurrenceSet) {
        e.appendChild(((LimitRecurrenceSet) expandOrLimitRecurrenceSet).toXml(factory));
      }
    }
    // limitFreebusySet
    if (limitFreeBusySet != null) {
      e.appendChild(limitFreeBusySet.toXml(factory));
    }
    return e;
  }

}
