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

package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.util.DateUtils;
import org.sakaiproject.nakamura.util.ISO8601Date;

import java.io.Serializable;
import java.text.ParseException;

public class CalendarURI extends URI implements Serializable {

  private static final long serialVersionUID = -20218593069459027L;

  private Date etag;

  private enum JSON_PROPERTIES {
    uri,
    etag
  }

  public CalendarURI(URI uri, Date etag) throws URIException {
    super(uri.toString(), false);
    this.etag = etag;
  }

  public CalendarURI(URI uri, String etag) throws URIException, ParseException {
    super(uri.toString(), false);

    try {
      this.etag = new DateTime(new ISO8601Date(etag).getTime());
    } catch (IllegalArgumentException ignored) {
      this.etag = new DateTime(etag.replaceAll("\"", ""), "yyyyMMdd'T'HHmmss", true);
    }

  }

  public CalendarURI(JSONObject json) throws JSONException, URIException {
    super(json.getString(JSON_PROPERTIES.uri.toString()), false);
    this.etag = new DateTime(new ISO8601Date(json.getString(JSON_PROPERTIES.etag.toString())).getTime());
  }

  public JSONObject toJSON() throws JSONException, URIException {
    JSONObject json = new JSONObject();
    json.put(JSON_PROPERTIES.uri.toString(), getURI());
    json.put(JSON_PROPERTIES.etag.toString(), DateUtils.iso8601(getEtag()));
    return json;
  }

  public Date getEtag() {
    return etag;
  }

}
