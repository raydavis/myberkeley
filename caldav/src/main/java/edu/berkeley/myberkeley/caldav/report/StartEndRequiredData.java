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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * <p/>
 * <!ELEMENT <xmlName> EMPTY>
 * <!ATTLIST <xmlName> start CDATA #REQUIRED
 * end   CDATA #REQUIRED>
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
