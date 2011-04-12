package edu.berkeley.myberkeley.caldav.report;

import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * <p/>
 * <!ELEMENT filter (comp-filter)>
 * <!ELEMENT comp-filter (is-not-defined |
 * (time-range?, prop-filter*, comp-filter*))>
 * <!ATTLIST comp-filter name CDATA #REQUIRED>
 *
 * @author ricky
 */
public class Filter implements XmlSerializable {

  private String name = null;
  private boolean isNotDefined = false;
  private TimeRange timeRange = null;
  private List<PropFilter> propFilter = new ArrayList<PropFilter>();
  private List<Filter> compFilter = new ArrayList<Filter>();

  public Filter(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isIsNotDefined() {
    return isNotDefined;
  }

  public void setIsNotDefined(boolean isNotDefined) {
    this.isNotDefined = isNotDefined;
  }

  public TimeRange getTimeRange() {
    return timeRange;
  }

  public void setTimeRange(TimeRange timeRange) {
    this.timeRange = timeRange;
  }

  public List<Filter> getCompFilter() {
    return compFilter;
  }

  public void setCompFilter(List<Filter> compFilter) {
    this.compFilter = compFilter;
  }

  public List<PropFilter> getPropFilter() {
    return propFilter;
  }

  private Element toXml(Document factory, boolean isInside) {
    Element e = DomUtil.createElement(factory,
            CalDavConstants.CALDAV_XML_COMP_FILTER, CalDavConstants.CALDAV_NAMESPACE);
    e.setAttribute(CalDavConstants.CALDAV_XML_COMP_FILTER_NAME, name);
    if (isNotDefined) {
      // is-not-defined
      e.appendChild(DomUtil.createElement(factory,
              CalDavConstants.CALDAV_XML_IS_NOT_DEFINED, CalDavConstants.CALDAV_NAMESPACE));
    } else {
      // (time-range?, prop-filter*, comp-filter*)
      // time-range
      if (timeRange != null) {
        e.appendChild(timeRange.toXml(factory));
      }
      // prop-filter
      for (PropFilter pf : propFilter) {
        e.appendChild(pf.toXml(factory));
      }
      // comp-filter
      for (Filter f : compFilter) {
        e.appendChild(f.toXml(factory, true));
      }
    }
    if (!isInside) {
      // not inside, it is a filter (not comp-filter)
      Element filter = DomUtil.createElement(factory,
              CalDavConstants.CALDAV_XML_FILTER, CalDavConstants.CALDAV_NAMESPACE);
      filter.appendChild(e);
      return filter;
    } else {
      // inside => only comp-filter returned
      return e;
    }
  }

  public Element toXml(Document factory) {
    return toXml(factory, false);
  }

}
