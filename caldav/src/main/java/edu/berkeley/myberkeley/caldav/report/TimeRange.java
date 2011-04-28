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

import java.util.Date;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * <p/>
 * <!ELEMENT time-range EMPTY>
 * <!ATTLIST time-range start CDATA #IMPLIED
 * end   CDATA #IMPLIED>
 * <p/>
 * Although it is IMPLIED instead of REQUIRED it will be used as
 * start and end were compulsory.
 *
 * @author ricky
 */
public class TimeRange extends StartEndRequiredData {

  public TimeRange(Date start, Date end) {
    super(CalDavConstants.CALDAV_XML_TIME_RANGE, start, end);
  }

}
