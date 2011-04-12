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
 * <!ELEMENT param-filter (is-not-defined | text-match?)>
 * <!ATTLIST param-filter name CDATA #REQUIRED>
 *
 * @author ricky
 */
public class ParamFilter implements XmlSerializable {

  private String name = null;
  private boolean isNotDefined = false;
  private TextMatch textMatch = null;

  public ParamFilter(String name) {
    this.name = name;
  }

  public ParamFilter(String name, TextMatch textMatch) {
    this.name = name;
    this.textMatch = textMatch;
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
    return textMatch;
  }

  public void setTextMatch(TextMatch textMatch) {
    this.textMatch = textMatch;
  }

  public Element toXml(Document factory) {
    Element e = DomUtil.createElement(factory,
            CalDavConstants.CALDAV_XML_PARAM_FILTER, CalDavConstants.CALDAV_NAMESPACE);
    // name attribute
    e.setAttribute(CalDavConstants.CALDAV_XML_PARAM_FILTER_NAME, name);
    // (is-not-defined | text-match?)
    if (isNotDefined) {
      // is-not-defined
      e.appendChild(DomUtil.createElement(factory,
              CalDavConstants.CALDAV_XML_IS_NOT_DEFINED, CalDavConstants.CALDAV_NAMESPACE));
    } else if (textMatch != null) {
      // text-match
      e.appendChild(textMatch.toXml(factory));
    }
    return e;
  }

}
