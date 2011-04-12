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

import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * <p/>
 * <!ELEMENT prop EMPTY>
 * <!ATTLIST prop name CDATA #REQUIRED
 * novalue (yes | no) "no">
 *
 * @author ricky
 */
public class Prop implements XmlSerializable {

  protected String name;
  protected String novalue = "no";

  public Prop(String name) {
    this.name = name;
    this.novalue = "no";
  }

  public void Prop(String name, boolean novalue) {
    this.name = name;
    this.novalue = (novalue) ? "yes" : "no";
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isNovalue() {
    return "yes".equals(novalue);
  }

  public void setNoValue(boolean novalue) {
    this.novalue = (novalue) ? "yes" : "no";
  }

  public void setNovalueToNo() {
    this.novalue = "no";
  }

  public void setNovalueToYes() {
    this.novalue = "yes";
  }

  public Element toXml(Document factory) {
    Element e = DomUtil.createElement(factory, CalDavConstants.CALDAV_XML_PROP, CalDavConstants.CALDAV_NAMESPACE);
    e.setAttribute(CalDavConstants.CALDAV_XML_PROP_NAME, name);
    if (isNovalue()) {
      // default "no" is not set
      e.setAttribute(CalDavConstants.CALDAV_XML_PROP_NOVALUE, novalue);
    }
    // return
    return e;
  }

}
