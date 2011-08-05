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
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Version;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Test;
import org.sakaiproject.nakamura.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

public class CalendarWrapperTest extends CalDavTests {

  @Test(expected = CalDavException.class)
  public void bogusCalendar() throws URIException, ParseException, CalDavException {
    CalendarBuilder builder = new CalendarBuilder();
    Calendar c = new Calendar();
    c.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    c.getProperties().add(Version.VERSION_2_0);
    c.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
    c.getComponents().add(tz);

    URI uri = new URI("foo", false);
    new CalendarWrapper(c, uri, RANDOM_ETAG);
  }

  @Test
  public void isCompletedArchivedAndRequired() throws URIException, ParseException, CalDavException {
    CalendarWrapper wrapper = getWrapper();
    Component todo = wrapper.getCalendar().getComponent(Component.VTODO);
    todo.getProperties().add(Status.VTODO_COMPLETED);
    todo.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
    todo.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);

    assertTrue(wrapper.isCompleted());
    assertTrue(wrapper.isArchived());
    assertTrue(wrapper.isRequired());
    assertFalse(wrapper.isRead());
  }

  @Test
  public void toJSON() throws URIException, ParseException, CalDavException, JSONException {
    CalendarWrapper wrapper = getWrapper();
    assertNotNull(wrapper.toJSON());
  }

  @Test(expected = CalDavException.class)
  public void badCallToFromJSON() throws CalDavException, JSONException {
    JSONObject bogus = new JSONObject();
    bogus.put("foo", "bar");
    new CalendarWrapper(bogus);
  }

  @Test
  public void vtodoFromJSON() throws CalDavException, IOException, JSONException {
    InputStream in = getClass().getClassLoader().getResourceAsStream("calendarWrapper_vtodo.json");
    String json = IOUtils.readFully(in, "utf-8");
    JSONObject jsonObject = new JSONObject(json);

    CalendarWrapper wrapper = new CalendarWrapper(jsonObject);
    assertNotNull(wrapper);
    assertEquals(wrapper.getComponent().getName(), Component.VTODO);

    // check for nondestructive deserialization
    assertEquals(wrapper, new CalendarWrapper(wrapper.toJSON()));
  }

  @Test
  public void veventFromJSON() throws CalDavException, IOException, JSONException {
    InputStream in = getClass().getClassLoader().getResourceAsStream("calendarWrapper_vevent.json");
    String json = IOUtils.readFully(in, "utf-8");
    JSONObject jsonObject = new JSONObject(json);

    CalendarWrapper wrapper = new CalendarWrapper(jsonObject);
    assertNotNull(wrapper);
    assertEquals(wrapper.getComponent().getName(), Component.VEVENT);
    assertNotNull(wrapper.getComponent().getProperty(Property.LOCATION));
    // check for nondestructive deserialization
    assertEquals(wrapper, new CalendarWrapper(wrapper.toJSON()));
    assertTrue(wrapper.isRead());
  }

  @Test
  public void verifyFromJSONIdempotent() throws CalDavException, IOException, JSONException, ParseException, InterruptedException {
    CalendarWrapper original = getWrapper();
    JSONObject json = original.toJSON();
    // ical4j sets the DTSTAMP of new Calendar instances to the datetime of the instance's creation.
    // when deserializing from content we replace that default DTSTAMP with what's in our data. So here
    // we sleep 1s to test that functionality.
    Thread.sleep(1000);
    CalendarWrapper deserialized = new CalendarWrapper(json);
    assertEquals(original, deserialized);
    assertEquals(original.hashCode(), deserialized.hashCode());
  }

  @Test
  public void generateNewUID() throws CalDavException, IOException, JSONException, ParseException {
    CalendarWrapper wrapper = getWrapper();
    String originalUID = wrapper.getComponent().getProperties().getProperty(Property.UID).getValue();
    wrapper.generateNewUID();
    String newUID = wrapper.getComponent().getProperties().getProperty(Property.UID).getValue();
    assertFalse(originalUID.equals(newUID));
  }

  private CalendarWrapper getWrapper() throws URIException, ParseException, CalDavException {
    Calendar c = buildVTodo("a todo");
    URI uri = new URI("foo", false);
    return new CalendarWrapper(c, uri, RANDOM_ETAG);
  }
}

