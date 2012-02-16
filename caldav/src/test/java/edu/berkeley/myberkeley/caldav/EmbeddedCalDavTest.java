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

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.caldav.api.BadRequestException;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.time.DateUtils;
import org.apache.sling.commons.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.util.ISO8601Date;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class EmbeddedCalDavTest extends CalDavTests {
  private static final String OWNER = "sclemens";
  private Repository repository;

  @Before
  public void setup() throws CalDavException, IOException, ClassNotFoundException, StorageClientException, AccessDeniedException {
    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    repository = baseMemoryRepository.getRepository();
    Session adminSession = repository.loginAdministrative();
    makeMinimalUser(OWNER, adminSession);
    adminConnector = new EmbeddedCalDav(OWNER, adminSession);
    adminConnector.ensureCalendarStore();
  }

  private void makeMinimalUser(String userId, Session adminSession) throws StorageClientException, AccessDeniedException {
    AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
    if (authorizableManager.findAuthorizable(userId) == null) {
      authorizableManager.createUser(userId, userId, "test", null);
      ContentManager contentManager = adminSession.getContentManager();
      String homePath = LitePersonalUtils.getHomePath(userId);
      if (!contentManager.exists(homePath)) {
        contentManager.update(new Content(homePath, ImmutableMap.of("sling:resourceType", (Object)"sakai/user-home")));
      }
    }
  }

  @Test
  public void filterExistingBedework() throws ParseException, URIException, CalDavException {
    // Model a Calendar after an existing Bedework-stored event during PDT
    // but not BST.
    CalendarBuilder builder = new CalendarBuilder();
    Calendar calendarBedework = new Calendar();
    calendarBedework.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    calendarBedework.getProperties().add(Version.VERSION_2_0);
    calendarBedework.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    TimeZone tzBedework = registry.getTimeZone("Europe/London");
    calendarBedework.getComponents().add(tzBedework.getVTimeZone());
    // Equals ""2012-03-14T09:00:00-07:00" due to 7 hour time difference.
    DateTime dateTimeBedework = new DateTime("20120314T160000", tzBedework);
    VEvent vevent = new VEvent(dateTimeBedework,
        new Dur(0, 1, 0, 0), "Event in English spring");
    vevent.getProperties().add(new Uid(UUID.randomUUID().toString()));
    calendarBedework.getComponents().add(vevent);
    CalendarWrapper wrapperBedework = new CalendarWrapper(calendarBedework, new URI("http://example.com/", true), "2012-02-11T10:05:39-08:00");
    
    // Configure search criteria to match Notification system.
    ISO8601Date startRangeRaw = new ISO8601Date("2012-03-14T08:30:00-07:00");
    ISO8601Date endRangeRaw = new ISO8601Date("2012-03-14T10:00:00-07:00");
    DateTime startRange = new DateTime(startRangeRaw.getTimeInMillis());
    DateTime endRange = new DateTime(endRangeRaw.getTimeInMillis());

    CalendarSearchCriteria criteria = new CalendarSearchCriteria();
    criteria.setStart(startRange);
    criteria.setEnd(endRange);
    assertTrue(EmbeddedCalFilter.isMatch(wrapperBedework, criteria));

    ISO8601Date startBadRaw = new ISO8601Date("2012-03-14T10:30:00-07:00");
    ISO8601Date endBadRaw = new ISO8601Date("2012-03-14T23:00:00-07:00");
    DateTime startBad = new DateTime(startBadRaw.getTimeInMillis());
    DateTime endBad = new DateTime(endBadRaw.getTimeInMillis());
    CalendarSearchCriteria badCriteria = new CalendarSearchCriteria();
    badCriteria.setStart(startBad);
    badCriteria.setEnd(endBad);
    assertFalse(EmbeddedCalFilter.isMatch(wrapperBedework, badCriteria));
  }

  @Test
  public void calResourcePathToStoragePath() {
    String segment = "joe/private/_myberkeley_calstore/SOMEUUID";
    String resourcePath = "http://localhost:8080/~" + segment + ".ics";
    assertEquals("a:" + segment, EmbeddedCalDav.calResourcePathToStoragePath(resourcePath));
  }

  // The methods below are slavishly copied from CalDavConnectorImplTest,
  // minus the restrictions on a user's ability to update their own store.

  @Test
  public void putCalendar() throws CalDavException {
    try {
      Calendar calendar = buildVevent("Created by CalDavTests");
      URI uri = this.adminConnector.putCalendar(calendar);
      boolean found = doesEntryExist(uri);
      assertTrue(found);
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void delete() throws CalDavException {
    try {
      Calendar calendar = buildVevent("Created by CalDavTests");
      CalendarURI uri = this.adminConnector.putCalendar(calendar);
      assertTrue(doesEntryExist(uri));
      this.adminConnector.deleteCalendar(uri);
      assertFalse(doesEntryExist(uri));
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test()
  public void getCalendars() throws CalDavException {
    try {
      List<CalendarURI> uris = this.adminConnector.getCalendarUris();
      this.adminConnector.getCalendars(uris);
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void putThenGetCalendarEntry() throws CalDavException, ParseException, URIException {
    try {
      Calendar originalCalendar = buildVevent("Created by CalDavTests");
      URI uri = this.adminConnector.putCalendar(originalCalendar);

      List<CalendarURI> uris = new ArrayList<CalendarURI>();
      uris.add(new CalendarURI(uri, RANDOM_ETAG));
      List<CalendarWrapper> calendars = this.adminConnector.getCalendars(uris);
      assertFalse(calendars.isEmpty());

      Calendar calOnServer = calendars.get(0).getCalendar();
      VEvent eventOnServer = (VEvent) calOnServer.getComponent(Component.VEVENT);
      VEvent originalEvent = (VEvent) originalCalendar.getComponent(Component.VEVENT);
      LOGGER.info("originalEvent = {}", originalEvent);
      LOGGER.info("eventOnServer = {}", eventOnServer);
      LOGGER.info("original start = {} , Date = {}", originalEvent.getStartDate(), originalEvent.getStartDate().getDate());
      LOGGER.info("fromServer start = {} , Date = {}", eventOnServer.getStartDate(), eventOnServer.getStartDate().getDate());

      assertEquals(originalEvent.getDuration(), eventOnServer.getDuration());
      assertEquals(originalEvent.getStartDate(), eventOnServer.getStartDate());
      assertEquals(originalEvent.getEndDate(), eventOnServer.getEndDate());
      assertEquals(originalEvent.getSummary(), eventOnServer.getSummary());
      assertEquals(originalEvent.getUid(), eventOnServer.getUid());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void searchForEvent() throws CalDavException {
    try {
      deleteAll();

      Calendar originalCalendar = buildVevent("Created by CalDavTests");
      this.adminConnector.putCalendar(originalCalendar);

      DateTime monthAgo = new DateTime(DateUtils.addDays(new Date(), -30));
      DateTime fourWeeksHence = new DateTime(DateUtils.addDays(new Date(), 28));

      // search for event just created, should find it
      CalendarSearchCriteria criteria = new CalendarSearchCriteria();
      criteria.setStart(monthAgo);
      criteria.setEnd(fourWeeksHence);
      criteria.setMode(CalendarSearchCriteria.MODE.UNREQUIRED);
      assertFalse(this.adminConnector.searchByDate(criteria).isEmpty());

      criteria.setMode(CalendarSearchCriteria.MODE.REQUIRED);
      assertTrue(this.adminConnector.searchByDate(criteria).isEmpty());

      criteria.setMode(CalendarSearchCriteria.MODE.ALL_ARCHIVED);
      assertTrue(this.adminConnector.searchByDate(criteria).isEmpty());

      criteria.setMode(CalendarSearchCriteria.MODE.ALL_UNARCHIVED);
      assertFalse(this.adminConnector.searchByDate(criteria).isEmpty());

      // search for a vtodo, there should be none
      criteria.setType(CalendarSearchCriteria.TYPE.VTODO);
      assertTrue(this.adminConnector.searchByDate(criteria).isEmpty());

      // search for an event but in a different time, should be none
      DateTime twoMonthsAgo = new DateTime(DateUtils.addDays(new Date(), -60));
      criteria.setStart(twoMonthsAgo);
      criteria.setEnd(monthAgo);
      criteria.setType(CalendarSearchCriteria.TYPE.VEVENT);
      assertTrue(this.adminConnector.searchByDate(criteria).isEmpty());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void searchForTodo() throws CalDavException {
    try {
      deleteAll();

      Calendar vtodo = buildVTodo("Archived VTODO");
      CalendarURI todoURI = this.adminConnector.putCalendar(vtodo);
      DateTime monthAgo = new DateTime(DateUtils.addDays(new Date(), -30));
      DateTime fourWeeksHence = new DateTime(DateUtils.addDays(new Date(), 28));

      CalendarSearchCriteria criteria = new CalendarSearchCriteria();
      criteria.setStart(monthAgo);
      criteria.setEnd(fourWeeksHence);
      criteria.setType(CalendarSearchCriteria.TYPE.VTODO);
      criteria.setMode(CalendarSearchCriteria.MODE.ALL_ARCHIVED);
      assertTrue(this.adminConnector.searchByDate(criteria).isEmpty());

      criteria.setMode(CalendarSearchCriteria.MODE.REQUIRED);
      assertFalse(this.adminConnector.searchByDate(criteria).isEmpty());

      // now archive the vtodo and search for it again
      Component vtodoComp = vtodo.getComponent(Component.VTODO);
      vtodoComp.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);
      this.adminConnector.modifyCalendar(todoURI, vtodo);

      assertTrue(this.adminConnector.searchByDate(criteria).isEmpty());

      criteria.setMode(CalendarSearchCriteria.MODE.ALL_ARCHIVED);
      assertFalse(this.adminConnector.searchByDate(criteria).isEmpty());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test(expected = BadRequestException.class)
  public void verifyUserUnableToPutAnAdminCreatedEvent() throws CalDavException, StorageClientException, AccessDeniedException {
    Session userSession = repository.loginAdministrative(OWNER);
    CalDavConnector userConnector = new EmbeddedCalDav(OWNER, userSession);
    try {
      Calendar originalCalendar = buildVevent("Created by CalDavTests");
      this.adminConnector.putCalendar(originalCalendar);
      userConnector.putCalendar(originalCalendar);
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
      throw new BadRequestException("", 500);
    }
  }

  @Test(expected = BadRequestException.class)
  public void verifyUserUnableToDelete() throws CalDavException, StorageClientException, AccessDeniedException {
    Session userSession = repository.loginAdministrative(OWNER);
    CalDavConnector userConnector = new EmbeddedCalDav(OWNER, userSession);
    try {
      Calendar originalCalendar = buildVevent("Created by CalDavTests");
      CalendarURI uri = this.adminConnector.putCalendar(originalCalendar);
      userConnector.deleteCalendar(uri);
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
      throw new BadRequestException("", 500);
    }
  }

  @Test
  public void putThenModify() throws CalDavException, ParseException, URIException {
    try {
      Calendar originalCalendar = buildVevent("Created by CalDavTests");
      CalendarURI uri = this.adminConnector.putCalendar(originalCalendar);

      long newStart = System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 2);
      VEvent vevent = (VEvent) originalCalendar.getComponent(Component.VEVENT);
      String newSummary = "Updated event";
      VEvent newVevent = new VEvent(new DateTime(newStart),
          new Dur(0, 1, 0, 0), newSummary);
      newVevent.getProperties().add(new Uid(UUID.randomUUID().toString()));
      originalCalendar.getComponents().remove(vevent);
      originalCalendar.getComponents().add(newVevent);

      this.adminConnector.modifyCalendar(uri, originalCalendar);

      List<CalendarURI> uris = new ArrayList<CalendarURI>();
      uris.add(new CalendarURI(uri, RANDOM_ETAG));
      List<CalendarWrapper> calendars = this.adminConnector.getCalendars(uris);
      assertFalse(calendars.isEmpty());
      Calendar newCalendar = calendars.get(0).getCalendar();
      VEvent updatedEvent = (VEvent) newCalendar.getComponent(Component.VEVENT);
      assertEquals(newSummary, updatedEvent.getSummary().getValue());
      assertEquals(new DateTime(newStart), updatedEvent.getStartDate().getDate());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test(expected = BadRequestException.class)
  public void modifyNonExistent() throws CalDavException, IOException, ParseException, JSONException {
    Calendar realCalendar = buildVevent("Created by CalDavTests");
    CalendarURI realUri = this.adminConnector.putCalendar(realCalendar);
    String realUriString = realUri.getURI();
    LOGGER.info("original URI = {}, getURI = {}", realUri, realUriString);
    String fakeUriString = realUriString.replace(".ics", "-not-there.ics");
    LOGGER.info("fake URI = {}", fakeUriString);
    try {
      Calendar calendar = buildVevent("Created by CalDavTests");
      CalendarURI uri = new CalendarURI(
          new URI(fakeUriString, false),
          new DateTime());
      this.adminConnector.modifyCalendar(uri, calendar);
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
      throw new BadRequestException("", 500);
    }
  }

  @Test
  public void putTodo() throws CalDavException, ParseException, URIException {
    try {
      Calendar calendar = buildVTodo("Todo created by CalDavTests");
      URI uri = this.adminConnector.putCalendar(calendar);

      List<CalendarURI> uris = new ArrayList<CalendarURI>(1);
      uris.add(new CalendarURI(uri, RANDOM_ETAG));
      List<CalendarWrapper> calendars = this.adminConnector.getCalendars(uris);
      Calendar calOnServer = calendars.get(0).getCalendar();
      VToDo vtodoOnServer = (VToDo) calOnServer.getComponent(Component.VTODO);
      VToDo originalVTodo = (VToDo) calendar.getComponent(Component.VTODO);

      assertEquals(originalVTodo.getDuration(), vtodoOnServer.getDuration());
      assertEquals(originalVTodo.getStartDate(), vtodoOnServer.getStartDate());
      assertEquals(originalVTodo.getDue(), vtodoOnServer.getDue());
      assertEquals(originalVTodo.getSummary(), vtodoOnServer.getSummary());
      assertEquals(originalVTodo.getUid(), vtodoOnServer.getUid());
      assertEquals(originalVTodo.getProperty(Property.CATEGORIES).getValue(),
          vtodoOnServer.getProperty(Property.CATEGORIES).getValue());
      assertEquals(CalDavConnector.MYBERKELEY_REQUIRED, vtodoOnServer.getProperty(Property.CATEGORIES));
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void hasNoOverdueTasks() throws CalDavException {
    try {
      assertFalse(this.adminConnector.hasOverdueTasks());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void hasAnOverdueTask() throws CalDavException {
    Calendar calendar = buildOverdueTask("Overdue test task");
    try {
      this.adminConnector.putCalendar(calendar);
      assertTrue(this.adminConnector.hasOverdueTasks());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void hasAPastTaskThatIsCompleted() throws CalDavException {
    CalendarBuilder builder = new CalendarBuilder();
    Calendar calendar = new Calendar();
    calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    calendar.getProperties().add(Version.VERSION_2_0);
    calendar.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
    calendar.getComponents().add(tz);
    DateTime due = new DateTime(DateUtils.addDays(new java.util.Date(), -1 * new Random().nextInt(28)));
    VToDo vtodo = new VToDo(due, due, "foo");
    vtodo.getProperties().add(new Uid(UUID.randomUUID().toString()));
    vtodo.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
    vtodo.getProperties().add(new Description("this is the description, it is long enough to wrap at the ical " +
        "specified standard 75th column"));
    vtodo.getProperties().add(Status.VTODO_COMPLETED);
    calendar.getComponents().add(vtodo);
    try {
      this.adminConnector.putCalendar(calendar);
      assertFalse(this.adminConnector.hasOverdueTasks());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }

  @Test
  public void hasAPastTaskThatIsArchived() throws CalDavException {
    CalendarBuilder builder = new CalendarBuilder();
    Calendar calendar = new Calendar();
    calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
    calendar.getProperties().add(Version.VERSION_2_0);
    calendar.getProperties().add(CalScale.GREGORIAN);
    TimeZoneRegistry registry = builder.getRegistry();
    VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
    calendar.getComponents().add(tz);
    DateTime due = new DateTime(DateUtils.addDays(new java.util.Date(), -1 * new Random().nextInt(28)));
    VToDo vtodo = new VToDo(due, due, "foo");
    vtodo.getProperties().add(new Uid(UUID.randomUUID().toString()));
    vtodo.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
    vtodo.getProperties().add(new Description("this is the description, it is long enough to wrap at the ical " +
        "specified standard 75th column"));
    vtodo.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);
    calendar.getComponents().add(vtodo);
    try {
      this.adminConnector.putCalendar(calendar);
      assertFalse(this.adminConnector.hasOverdueTasks());
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting server", ioe);
    }
  }
}
