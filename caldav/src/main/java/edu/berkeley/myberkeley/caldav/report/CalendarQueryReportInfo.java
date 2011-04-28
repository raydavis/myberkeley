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
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 *
 * @author ricky
 */
public class CalendarQueryReportInfo extends ReportInfo {

  private RequestCalendarData calendarData = null;
  private Filter filter = null;
  private String timezone = null;

  public CalendarQueryReportInfo(RequestCalendarData calendarData, Filter filter) {
    super(CalendarQueryReport.CALENDAR_QUERY, DavConstants.DEPTH_1, CalDavConstants.ETAG);
    this.calendarData = calendarData;
    this.filter = filter;
    this.timezone = null;
  }

  @Override
  public Element toXml(Document document) {
    // create calendar-multiget element
    Element calendarQuery = DomUtil.createElement(document,
            CalendarQueryReport.CALENDAR_QUERY.getLocalName(),
            CalendarQueryReport.CALENDAR_QUERY.getNamespace());
    calendarQuery.setAttributeNS(Namespace.XMLNS_NAMESPACE.getURI(),
            Namespace.XMLNS_NAMESPACE.getPrefix() + ":" + DavConstants.NAMESPACE.getPrefix(),
            DavConstants.NAMESPACE.getURI());
    // append props
    Element prop = getPropertyNameSet().toXml(document);
    // append calendar-data request info
    prop.appendChild(calendarData.toXml(document));
    // append prop to calendarMultiGet
    calendarQuery.appendChild(prop);
    // append filter
    calendarQuery.appendChild(filter.toXml(document));
    // timezone
    if (timezone != null) {
      Element timezoneElement = DomUtil.createElement(document,
              CalDavConstants.CALDAV_XML_TIMEZONE, CalDavConstants.CALDAV_NAMESPACE);
      calendarQuery.appendChild(timezoneElement);
    }
    return calendarQuery;
  }

}

