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

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.report.Report;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 *
 * @author ricky
 */
public class CalendarQueryReport implements Report, DeltaVConstants {
  public static final ReportType CALENDAR_QUERY = ReportType.register(
          CalDavConstants.CALDAV_XML_CALENDAR_QUERY,
          CalDavConstants.CALDAV_NAMESPACE,
          CalendarMultiGetReport.class);

  public ReportType getType() {
    return CALENDAR_QUERY;
  }

  public boolean isMultiStatusReport() {
    return true;
  }

  public void init(DavResource dr, ReportInfo ri) throws DavException {
  }

  public Element toXml(Document dcmnt) {
    throw new UnsupportedOperationException("Not supported yet.");
  }


}
