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

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.util.DateUtils;
import org.sakaiproject.nakamura.util.ISO8601Date;

import java.io.Serializable;
import java.text.ParseException;
import java.util.UUID;

public class CalendarWrapper implements Serializable {

  private static final long serialVersionUID = -5997627557793464309L;

  public enum JSON_PROPERTY_NAMES {
    uri,
    etag,
    component,
    isRequired,
    isCompleted,
    isArchived,
    isRead,
    icalData
  }

  public enum ICAL_DATA_PROPERTY_NAMES {
    DTSTAMP,
    DTSTART,
    DUE,
    SUMMARY,
    DESCRIPTION,
    CATEGORIES,
    STATUS,
    UID,
    LOCATION
  }

  private Calendar calendar;

  private CalendarURI calendarUri;

  private Component component;

  public CalendarWrapper(Calendar calendar, URI uri, String etag) throws CalDavException {
    this.calendar = calendar;
    this.component = calendar.getComponent(Component.VEVENT);
    if (this.component == null) {
      this.component = calendar.getComponent(Component.VTODO);
    }
    if (this.component == null) {
      throw new CalDavException("Unsupported ical data - passed calendar had no VTODO or VEVENT", null);
    }
    try {
      this.calendarUri = new CalendarURI(uri, etag);
    } catch (ParseException pe) {
      throw new CalDavException("Exception parsing date '" + etag + "'", pe);
    } catch (URIException uie) {
      throw new CalDavException("Exception parsing uri '" + uri + "'", uie);
    }
  }

  public CalendarWrapper(JSONObject json) throws CalDavException {
    try {
      String uri = json.getString(JSON_PROPERTY_NAMES.uri.toString());
      String etag = json.getString(JSON_PROPERTY_NAMES.etag.toString());
      String componentName = json.getString(JSON_PROPERTY_NAMES.component.toString());

      try {
        this.calendarUri = new CalendarURI(new URI(uri, false), etag);
      } catch (ParseException pe) {
        throw new CalDavException("Exception parsing date '" + etag + "'", pe);
      } catch (URIException uie) {
        throw new CalDavException("Exception parsing uri '" + uri + "'", uie);
      }

      // build the ical Calendar object itself
      CalendarBuilder builder = new CalendarBuilder();
      this.calendar = new Calendar();
      this.calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
      this.calendar.getProperties().add(Version.VERSION_2_0);
      this.calendar.getProperties().add(CalScale.GREGORIAN);
      TimeZoneRegistry registry = builder.getRegistry();
      VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
      this.calendar.getComponents().add(tz);

      JSONObject icalData = json.getJSONObject(JSON_PROPERTY_NAMES.icalData.toString());
      if (icalData == null) {
        throw new CalDavException("No valid icalData found in JSON", null);
      }
      String summary = icalData.getString(ICAL_DATA_PROPERTY_NAMES.SUMMARY.toString());
      DateTime startDate = new DateTime();
      try {
        startDate = getDateTimeFromJSON(icalData.getString(ICAL_DATA_PROPERTY_NAMES.DTSTART.toString()), registry);
      } catch (JSONException ignored) {
      }

      if (Component.VEVENT.equals(componentName)) {
        this.component = new VEvent(startDate, summary);
      } else if (Component.VTODO.equals(componentName)) {
        DateTime due = getDateTimeFromJSON(icalData.getString(ICAL_DATA_PROPERTY_NAMES.DUE.toString()), registry);
        this.component = new VToDo(startDate, due, summary);
      } else {
        throw new CalDavException("Unsupported component type " + componentName, null);
      }

      // ical4j sets the DTSTAMP of new Calendar instances to the datetime of the instance's creation.
      // when deserializing from content we replace that default DTSTAMP with what's in our data.
      try {
        DateTime jsonDtStamp = getDateTimeFromJSON(icalData.getString(ICAL_DATA_PROPERTY_NAMES.DTSTAMP.toString()), registry);

        int dtStampIndex = 0;
        for (int i = 0; i < this.component.getProperties().size(); i++) {
          Object o = this.getComponent().getProperties().get(i);
          if (o instanceof DtStamp) {
            dtStampIndex = i;
            break;
          }
        }
        this.component.getProperties().remove(dtStampIndex);
        this.component.getProperties().add(dtStampIndex, new DtStamp(jsonDtStamp));
      } catch (JSONException ignored) {
      }

      String uid = UUID.randomUUID().toString();
      try {
        uid = icalData.getString(ICAL_DATA_PROPERTY_NAMES.UID.toString());
      } catch (JSONException ignored) {
      }

      this.component.getProperties().add(new Uid(uid));

      // handle optional props
      try {
        JSONArray categories = icalData.getJSONArray(ICAL_DATA_PROPERTY_NAMES.CATEGORIES.toString());
        for (int i = 0; i < categories.length(); i++) {
          String cat = categories.getString(i);
          this.component.getProperties().add(new Categories(cat));
        }
      } catch (JSONException ignored) {

      }

      try {
        String description = icalData.getString(ICAL_DATA_PROPERTY_NAMES.DESCRIPTION.toString());
        this.component.getProperties().add(new Description(description));
      } catch (JSONException ignored) {

      }

      try {
        String status = icalData.getString(ICAL_DATA_PROPERTY_NAMES.STATUS.toString());
        this.component.getProperties().add(new Status(status));
      } catch (JSONException ignored) {

      }

      try {
        String loc = icalData.getString(ICAL_DATA_PROPERTY_NAMES.LOCATION.toString());
        this.component.getProperties().add(new Location(loc));
      } catch (JSONException ignored) {

      }

      this.calendar.getComponents().add(this.component);

    } catch (JSONException je) {
      throw new CalDavException("Invalid json data passed", je);
    }
  }

  public JSONObject toJSON() throws JSONException {
    JSONObject icalData = new JSONObject();
    JSONArray categoriesArray = new JSONArray();
    boolean isRequired = false;
    boolean isArchived = false;
    boolean isCompleted = false;
    boolean isRead = false;

    PropertyList propertyList = this.component.getProperties();
    for (Object o : propertyList) {
      Property property = (Property) o;
      String value = property.getValue();

      if (property instanceof DateProperty) {
        DateProperty start = (DateProperty) property;
        value = DateUtils.iso8601(start.getDate());
      } else if (property instanceof Status) {
        if (Status.VTODO_COMPLETED.getValue().equals(value)) {
          isCompleted = true;
        }
      } else if (property instanceof Categories) {
        categoriesArray.put(value);
        if (CalDavConnector.MYBERKELEY_ARCHIVED.getValue().equals(value)) {
          isArchived = true;
        }
        if (CalDavConnector.MYBERKELEY_REQUIRED.getValue().equals(value)) {
          isRequired = true;
        }
        if (CalDavConnector.MYBERKELEY_READ.getValue().equals(value)) {
          isRead = true;
        }
      }
      icalData.put(property.getName(), value);
    }

    icalData.put(Property.CATEGORIES, categoriesArray);

    JSONObject json = new JSONObject();
    json.put(JSON_PROPERTY_NAMES.uri.toString(), getUri().toString());
    json.put(JSON_PROPERTY_NAMES.etag.toString(), DateUtils.iso8601(getEtag()));
    json.put(JSON_PROPERTY_NAMES.component.toString(), getComponent().getName());
    json.put(JSON_PROPERTY_NAMES.isRequired.toString(), isRequired);
    json.put(JSON_PROPERTY_NAMES.isArchived.toString(), isArchived);
    json.put(JSON_PROPERTY_NAMES.isCompleted.toString(), isCompleted);
    json.put(JSON_PROPERTY_NAMES.isRead.toString(), isRead);
    json.put(JSON_PROPERTY_NAMES.icalData.toString(), icalData);
    return json;
  }

  public void generateNewUID() {
    // give component a new UID so it will be unique in Bedework
    ((Uid) this.getComponent().getProperties().getProperty(Property.UID)).setValue(UUID.randomUUID().toString());
  }

  public Calendar getCalendar() {
    return this.calendar;
  }

  public Component getComponent() {
    return this.component;
  }

  public CalendarURI getUri() {
    return this.calendarUri;
  }

  public Date getEtag() {
    return this.calendarUri.getEtag();
  }

  public boolean isCompleted() {
    PropertyList propList = this.component.getProperties(Property.STATUS);
    return propList != null && propList.contains(Status.VTODO_COMPLETED);
  }

  public boolean isRequired() {
    PropertyList propList = this.component.getProperties(Property.CATEGORIES);
    return propList != null && propList.contains(CalDavConnector.MYBERKELEY_REQUIRED);
  }

  public boolean isArchived() {
    PropertyList propList = this.component.getProperties(Property.CATEGORIES);
    return propList != null && propList.contains(CalDavConnector.MYBERKELEY_ARCHIVED);
  }

  public boolean isRead() {
      PropertyList propList = this.component.getProperties(Property.CATEGORIES);
      return propList != null && propList.contains(CalDavConnector.MYBERKELEY_READ);
    }

  private DateTime getDateTimeFromJSON(String json, TimeZoneRegistry registry) {
    ISO8601Date dateISO8601 = new ISO8601Date(json);
    DateTime ret = new DateTime();
    ret.setTime(dateISO8601.getTimeInMillis());
    ret.setTimeZone(registry.getTimeZone("Europe/London")); // assume input is GMT
    return ret;
  }

  @Override
  public String toString() {
    return "CalendarWrapper{" +
            "uri='" + getUri().toString() + '\'' +
            "etag=" + getUri().getEtag() +
            ",calendar=" + this.calendar +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CalendarWrapper that = (CalendarWrapper) o;
    return !(this.calendar != null ? !this.calendar.equals(that.calendar) : that.calendar != null)
            && !(this.calendarUri != null ? !this.calendarUri.equals(that.calendarUri) : that.calendarUri != null)
            && !(this.component != null ? !this.component.equals(that.component) : that.component != null);
  }

  @Override
  public int hashCode() {
    int result = this.calendar != null ? this.calendar.hashCode() : 0;
    result = 31 * result + (this.calendarUri != null ? this.calendarUri.hashCode() : 0);
    result = 31 * result + (this.component != null ? this.component.hashCode() : 0);
    return result;
  }
}
