/*

  * Licensed to the Sakai Foundation (SF) under one
  * or more contributor license agreements. See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership. The SF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License. You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied. See the License for the
  * specific language governing permissions and limitations under the License.

 */

package edu.berkeley.myberkeley.caldav.report;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 *
 * @author ricky
 */
public class CalendarMultiGetReportInfo extends ReportInfo {

  RequestCalendarData calendarData = null;
  List<String> hrefs = null;

  public CalendarMultiGetReportInfo(RequestCalendarData calendarData, List<String> hrefs) {
    super(CalendarMultiGetReport.CALENDAR_MULTI_GET, DavConstants.DEPTH_0, CalDavConstants.ETAG);
    this.calendarData = calendarData;
    this.hrefs = hrefs;
  }

  public CalendarMultiGetReportInfo(DavPropertyNameSet propertyNames,
                                    RequestCalendarData calendarData, List<String> hrefs) {
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
    for (String s : this.hrefs) {
      Element href = DomUtil.createElement(document,
              DavConstants.XML_HREF, DavConstants.NAMESPACE, s);
      calendarMultiGet.appendChild(href);
    }
    return calendarMultiGet;
  }

}
