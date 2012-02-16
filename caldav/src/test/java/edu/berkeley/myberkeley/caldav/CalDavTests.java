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

import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.StreetAddress;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.apache.commons.httpclient.URI;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Assert;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public abstract class CalDavTests extends Assert {

  protected static final String RANDOM_ETAG = "20110316T191659Z-0";

  protected static final String MONTH_AFTER_RANDOM_ETAG = "20110416T191659Z-0";

  protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CalDavConnectorImplTest.class);

  protected CalDavConnector adminConnector;
  protected String calDavServer = null;
  protected String calDavPassword = null;

  protected void deleteAll() throws CalDavException {
    try {
      List<CalendarURI> uris = this.adminConnector.getCalendarUris();
      for (CalendarURI uri : uris) {
        this.adminConnector.deleteCalendar(uri);
      }
      assertTrue(this.adminConnector.getCalendarUris().isEmpty());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  protected Calendar buildVevent(String summary) {
    CalendarBuilder builder = new CalendarBuilder();
    Calendar c = new Calendar();
    c.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    c.getProperties().add(Version.VERSION_2_0);
    c.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
    c.getComponents().add(tz);
    DateTime start = new DateTime(DateUtils.addDays(new Date(), new Random().nextInt(28)));
    start.setUtc(true);
    LOGGER.info("New start time = {}", start);

    VEvent vevent = new VEvent(start,
            new Dur(0, 1, 0, 0), summary);
    vevent.getProperties().add(new Description("this is the description, it is long enough to wrap at the ical " +
            "specified standard 75th column"));
    vevent.getProperties().add(new Uid(UUID.randomUUID().toString()));
    vevent.getProperties().add(new Categories("LOLCAT"));
    vevent.getProperties().add(new Categories("FATCAT"));
    vevent.getProperties().add(new Location("Zellerbach Hall"));
    vevent.getProperties().add(new StreetAddress("Somewhere on campus"));
    c.getComponents().add(vevent);
    return c;
  }

  protected Calendar buildVTodo(String summary) {
    CalendarBuilder builder = new CalendarBuilder();
    Calendar calendar = new Calendar();
    calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    calendar.getProperties().add(Version.VERSION_2_0);
    calendar.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
    calendar.getComponents().add(tz);
    DateTime due = new DateTime(DateUtils.addDays(new Date(), new Random().nextInt(28)));
    due.setUtc(true);
    VToDo vtodo = new VToDo(due, due, summary);
    vtodo.getProperties().add(new Uid(UUID.randomUUID().toString()));
    vtodo.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
    vtodo.getProperties().add(new Description("this is the description, it is long enough to wrap at the ical " +
            "specified standard 75th column"));
    vtodo.getProperties().add(Status.VTODO_NEEDS_ACTION);
    calendar.getComponents().add(vtodo);
    return calendar;
  }

  protected Calendar buildOverdueTask(String summary) {
    CalendarBuilder builder = new CalendarBuilder();
    Calendar calendar = new Calendar();
    calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    calendar.getProperties().add(Version.VERSION_2_0);
    calendar.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
    calendar.getComponents().add(tz);
    DateTime due = new DateTime(DateUtils.addDays(new java.util.Date(), -1 * new Random().nextInt(28)));
    due.setUtc(true);
    VToDo vtodo = new VToDo(due, due, summary);
    vtodo.getProperties().add(new Uid(UUID.randomUUID().toString()));
    vtodo.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
    vtodo.getProperties().add(new Description("this is the description, it is long enough to wrap at the ical " +
            "specified standard 75th column"));
    vtodo.getProperties().add(Status.VTODO_NEEDS_ACTION);
    calendar.getComponents().add(vtodo);
    return calendar;
  }

  protected Calendar buildPastEvent(String summary) {
    CalendarBuilder builder = new CalendarBuilder();
    Calendar c = new Calendar();
    c.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    c.getProperties().add(Version.VERSION_2_0);
    c.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
    c.getComponents().add(tz);
    DateTime start = new DateTime(DateUtils.addDays(new Date(), -1 * new Random().nextInt(28)));
    start.setUtc(true);
    VEvent vevent = new VEvent(start,
            new Dur(0, 1, 0, 0), summary);
    vevent.getProperties().add(new Description("this is the description, it is long enough to wrap at the ical " +
            "specified standard 75th column"));
    vevent.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
    vevent.getProperties().add(new Uid(UUID.randomUUID().toString()));
    c.getComponents().add(vevent);
    return c;
  }

  protected boolean doesEntryExist(URI uri) throws CalDavException, IOException {
    for (CalendarURI thisURI : this.adminConnector.getCalendarUris()) {
      if ((thisURI.toString()).equals(uri.toString())) {
        return true;
      }
    }
    return false;
  }
  
  protected boolean initializeCalDavSource() {
    this.calDavServer = StringUtils.trimToNull(System.getProperty("caldav.server"));
    this.calDavPassword = StringUtils.trimToNull(System.getProperty("caldav.password"));
    return ((this.calDavServer != null) && (this.calDavPassword != null));
  }
}
