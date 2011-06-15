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

package edu.berkeley.myberkeley.notifications;

import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.content.Content;

public class CalendarNotification extends Notification {

  private CalendarWrapper wrapper;

  public CalendarNotification(JSONObject json) throws JSONException, CalDavException {
    super(json);
    this.wrapper = new CalendarWrapper(json.getJSONObject(JSON_PROPERTIES.calendarWrapper.toString()));
  }

  public CalendarNotification(Content content) throws JSONException, CalDavException {
    super(content);
    this.wrapper = new CalendarWrapper(new JSONObject((String) content.getProperty(JSON_PROPERTIES.calendarWrapper.toString())));
  }

  public CalendarWrapper getWrapper() {
    return this.wrapper;
  }

  @Override
  public void toContent(String storePath, Content content) throws JSONException {
    super.toContent(storePath, content);
    content.setProperty(JSON_PROPERTIES.calendarWrapper.toString(), this.getWrapper().toJSON().toString());
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (this.wrapper != null ? this.wrapper.hashCode() : 0);
    return result;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  @Override
  public boolean equals(Object o) {
    if ( this == o ) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    CalendarNotification that = (CalendarNotification) o;
    if (this.wrapper != null ? !this.wrapper.equals(that.wrapper) : that.wrapper != null) return false;
    return true;
  }
}
