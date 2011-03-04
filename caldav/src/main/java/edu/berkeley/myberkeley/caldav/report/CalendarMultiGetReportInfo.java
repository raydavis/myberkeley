package edu.berkeley.myberkeley.caldav.report;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author ricky
 */
public class CalendarMultiGetReportInfo extends ReportInfo {

    RequestCalendarData calendarData = null;
    String[] hrefs = null;

    public CalendarMultiGetReportInfo(RequestCalendarData calendarData, String[] hrefs) {
        super(CalendarMultiGetReport.CALENDAR_MULTI_GET, DavConstants.DEPTH_0, null);
        this.calendarData = calendarData;
        this.hrefs = hrefs;
    }

    public CalendarMultiGetReportInfo(DavPropertyNameSet propertyNames,
            RequestCalendarData calendarData, String[] hrefs) {
        super(CalendarMultiGetReport.CALENDAR_MULTI_GET, DavConstants.DEPTH_0, propertyNames);
        this.calendarData = calendarData;
        this.hrefs = hrefs;
    }

    @Override
    public Element toXml(Document document) {
        // create calendar-multiget element
        Element calendarMultiGet = DomUtil.createElement(document,
                CalendarMultiGetReport.CALENDAR_MULTI_GET.getLocalName(),
                CalendarMultiGetReport.CALENDAR_MULTI_GET.getNamespace());
        calendarMultiGet.setAttributeNS(Namespace.XMLNS_NAMESPACE.getURI(),
                    Namespace.XMLNS_NAMESPACE.getPrefix() + ":" + DavConstants.NAMESPACE.getPrefix(),
                    DavConstants.NAMESPACE.getURI());
        // append props
        Element prop = getPropertyNameSet().toXml(document);
        // append calendar-data request info
        prop.appendChild(calendarData.toXml(document));
        // append prop to calendarMultiGet
        calendarMultiGet.appendChild(prop);
        // append hrefs
        for (int i = 0; i < hrefs.length; i++) {
            Element href = DomUtil.createElement(document,
                    DavConstants.XML_HREF, DavConstants.NAMESPACE, hrefs[i]);
            calendarMultiGet.appendChild(href);
        }
        return calendarMultiGet;
    }

}
