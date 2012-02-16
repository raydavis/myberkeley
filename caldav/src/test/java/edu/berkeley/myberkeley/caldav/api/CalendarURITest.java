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

package edu.berkeley.myberkeley.caldav.api;

import edu.berkeley.myberkeley.caldav.CalDavTests;
import net.fortuna.ical4j.model.Date;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Test;

public class CalendarURITest extends CalDavTests {

  @Test
  public void toJSONAndBackAgain() throws URIException, JSONException {
    CalendarURI uri = new CalendarURI(new URI("/foo", false), new Date());
    JSONObject json = uri.toJSON();
    CalendarURI deserialized = new CalendarURI(json);
    assertEquals(uri, deserialized);
    assertEquals(uri.getEtag(), deserialized.getEtag());
  }

}
