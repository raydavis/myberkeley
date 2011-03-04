package edu.berkeley.myberkeley.caldav.report;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * <!ELEMENT <xmlName> EMPTY>
 * <!ATTLIST <xmlName> start CDATA #REQUIRED
 *                     end   CDATA #REQUIRED>
 *
 * @author ricky
 */
public abstract class StartEndRequiredData implements XmlSerializable {

    static private final SimpleDateFormat format =
            new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

    private String xmlName = null;
    private Date start = null;
    private Date end = null;

    public StartEndRequiredData(String xmlName, Date start, Date end) {
        this.xmlName = xmlName;
        this.start = start;
        this.end = end;
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(Date end) {
        this.end = end;
    }

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Element toXml(Document factory) {
        Element e = DomUtil.createElement(factory, xmlName, CalDavConstants.CALDAV_NAMESPACE);
        e.setAttribute(CalDavConstants.CALDAV_XML_END, format.format(end));
        e.setAttribute(CalDavConstants.CALDAV_XML_START, format.format(start));
        return e;
    }

    static {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
}
