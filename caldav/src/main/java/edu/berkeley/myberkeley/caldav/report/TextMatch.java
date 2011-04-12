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
 * <!ELEMENT text-match (#PCDATA)>
 * <!ATTLIST text-match collation CDATA "i;ascii-casemap"
 * negate-condition (yes | no) "no">
 *
 * @author ricky
 */
public class TextMatch implements XmlSerializable {

  private String collation = "i;ascii-casemap";
  private String negateCondition = "no";
  private String value;

  public TextMatch(String value) {
    this.value = value;
  }

  public TextMatch(String value, boolean negateCondition) {
    this.value = value;
    this.negateCondition = negateCondition ? "yes" : "no";
  }

  public String getCollation() {
    return collation;
  }

  public void setCollation(String collation) {
    this.collation = collation;
  }

  public boolean isNegateCondition() {
    return "yes".equals(negateCondition);
  }

  public void setNegateCondition(boolean negateCondition) {
    this.negateCondition = negateCondition ? "yes" : "no";
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Element toXml(Document factory) {
    Element e = DomUtil.createElement(factory,
            CalDavConstants.CALDAV_XML_TEXT_MATCH, CalDavConstants.CALDAV_NAMESPACE);
    // collation
    e.setAttribute(CalDavConstants.CALDAV_XML_COLLATION, collation);
    // negate-condition
    if (isNegateCondition()) {
      // ony if yes
      e.setAttribute(CalDavConstants.CALDAV_XML_NEGATE_CONDITION, collation);
    }
    // set the value
    e.setTextContent(value);
    return e;
  }

}
