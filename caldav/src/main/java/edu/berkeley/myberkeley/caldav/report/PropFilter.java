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
 * <!ELEMENT prop-filter (is-not-defined |
 * ((time-range | text-match)?, param-filter*))>
 * <!ATTLIST prop-filter name CDATA #REQUIRED>
 *
 * @author ricky
 */
public class PropFilter implements XmlSerializable {

  private String name = null;
  private boolean isNotDefined = false;
  private Object timeRangeOrTextMatch = null;
  private List<ParamFilter> paramFilter = new ArrayList<ParamFilter>();

  public PropFilter(String name) {
    this.name = name;
  }

  public boolean isIsNotDefined() {
    return isNotDefined;
  }

  public void setIsNotDefined(boolean isNotDefined) {
    this.isNotDefined = isNotDefined;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public TextMatch getTextMatch() {
    if (timeRangeOrTextMatch instanceof TextMatch) {
      return (TextMatch) timeRangeOrTextMatch;
    } else {
      return null;
    }
  }

  public void setTextMatch(TextMatch textMatch) {
    this.timeRangeOrTextMatch = textMatch;
  }

  public TimeRange getTimeRange() {
    if (timeRangeOrTextMatch instanceof TimeRange) {
      return (TimeRange) timeRangeOrTextMatch;
    } else {
      return null;
    }
  }

  public void setTimeRange(TimeRange timeRange) {
    this.timeRangeOrTextMatch = timeRange;
  }

  public List<ParamFilter> getParamFilter() {
    return paramFilter;
  }

  public Element toXml(Document factory) {
    Element e = DomUtil.createElement(factory,
            CalDavConstants.CALDAV_XML_PROP_FILTER, CalDavConstants.CALDAV_NAMESPACE);
    if (isNotDefined) {
      // is-not-defined
      e.appendChild(DomUtil.createElement(factory,
              CalDavConstants.CALDAV_XML_IS_NOT_DEFINED, CalDavConstants.CALDAV_NAMESPACE));
    } else {
      // (time-range | text-match)?, param-filter*)
      //(time-range | text-match)?
      if (timeRangeOrTextMatch != null) {
        if (timeRangeOrTextMatch instanceof TimeRange) {
          e.appendChild(((TimeRange) timeRangeOrTextMatch).toXml(factory));
        } else if (timeRangeOrTextMatch instanceof TextMatch) {
          e.appendChild(((TextMatch) timeRangeOrTextMatch).toXml(factory));
        }
      }
      // param-filter
      for (ParamFilter pf : paramFilter) {
        pf.toXml(factory);
      }
    }
    return e;
  }

}
