package edu.berkeley.myberkeley.caldav;

import junit.framework.Assert;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This test is really an integration test used to exercise (and learn) CalDAV functionality.
 * It assumes test.media.berkeley.edu is up and running Bedework on port 8080.
 */

public class CalDavConnectorTest extends Assert {

    private static final String SERVER_ROOT = "http://test.media.berkeley.edu:8080";

    private static final String USER_HOME = SERVER_ROOT + "/ucaldav/user/vbede/calendar/";

    private static final String OWNER = "vbede";

    private CalDavConnector adminConnector;

    private CalDavConnector userConnector;

    @Before
    public void setup() throws CalDavException {
        this.adminConnector = new CalDavConnector("admin", "bedework", USER_HOME);
        this.userConnector = new CalDavConnector("vbede", "bedework", USER_HOME);
    }

    @Test
    public void getOptions() throws CalDavException {
        this.adminConnector.getOptions();
    }

    @Test
    public void putCalendar() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        String uri = USER_HOME + uuid.toString() + ".ics";

        Calendar calendar = buildVevent(uuid);
        this.adminConnector.putCalendar(uri, calendar, OWNER);

        boolean found = doesEntryExist(uri);
        assertTrue(found);
    }

    @Test
    public void delete() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        String uri = USER_HOME + uuid.toString() + ".ics";

        Calendar calendar = buildVevent(uuid);
        this.adminConnector.putCalendar(uri, calendar, OWNER);
        assertTrue(doesEntryExist(uri));
        this.adminConnector.deleteCalendar(uri);
        assertFalse(doesEntryExist(uri));
    }

    @Ignore
    @Test
    public void deleteAll() throws CalDavException {
        List<String> uris = this.adminConnector.getAllUris();
        for (String uri : uris) {
            this.adminConnector.deleteCalendar(SERVER_ROOT + uri);
        }
    }

    @Test
    public void getCalendars() throws CalDavException {
        List<String> uris = this.adminConnector.getAllUris();
        this.adminConnector.getCalendars(uris);
    }

    @Test
    public void putThenGetCalendarEntry() throws CalDavException {

        UUID uuid = UUID.randomUUID();
        String uri = USER_HOME + uuid.toString() + ".ics";
        Calendar originalCalendar = buildVevent(uuid);
        this.adminConnector.putCalendar(uri, originalCalendar, OWNER);

        List<String> uris = new ArrayList<String>();
        uris.add(uri);
        List<Calendar> calendars = this.adminConnector.getCalendars(uris);
        assertFalse(calendars.isEmpty());

        Calendar calOnServer = calendars.get(0);
        VEvent eventOnServer = (VEvent) calOnServer.getComponent(Component.VEVENT);
        VEvent originalEvent = (VEvent) originalCalendar.getComponent(Component.VEVENT);

        assertEquals(originalEvent.getDuration(), eventOnServer.getDuration());
        assertEquals(originalEvent.getStartDate(), eventOnServer.getStartDate());
        assertEquals(originalEvent.getEndDate(), eventOnServer.getEndDate());
        assertEquals(originalEvent.getSummary(), eventOnServer.getSummary());
        assertEquals(originalEvent.getUid(), eventOnServer.getUid());

    }

    @Test(expected = BadRequestException.class)
    public void verifyUserUnableToPutAnAdminCreatedEvent() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        String uri = USER_HOME + uuid.toString() + ".ics";
        Calendar originalCalendar = buildVevent(uuid);
        this.adminConnector.putCalendar(uri, originalCalendar, OWNER);
        this.userConnector.putCalendar(uri, originalCalendar, OWNER);
    }

    @Test(expected = BadRequestException.class)
    public void verifyUserUnableToDelete() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        String uri = USER_HOME + uuid.toString() + ".ics";
        Calendar originalCalendar = buildVevent(uuid);
        this.adminConnector.putCalendar(uri, originalCalendar, OWNER);
        this.userConnector.deleteCalendar(uri);
    }

    @Test(expected = BadRequestException.class)
    public void verifyUserAbleToPutOwnEvent() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        String uri = USER_HOME + uuid.toString() + ".ics";
        Calendar originalCalendar = buildVevent(uuid);
        this.userConnector.putCalendar(uri, originalCalendar, OWNER);
    }

    @Test
    public void putThenModify() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        String uri = USER_HOME + uuid.toString() + ".ics";
        Calendar originalCalendar = buildVevent(uuid);
        this.adminConnector.putCalendar(uri, originalCalendar, OWNER);

        long newStart = System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 2);
        VEvent vevent = (VEvent) originalCalendar.getComponent(Component.VEVENT);
        String newSummary = "Updated event";
        VEvent newVevent = new VEvent(new DateTime(newStart),
                new Dur(0, 1, 0, 0), newSummary);
        newVevent.getProperties().add(new Uid(uuid.toString()));
        originalCalendar.getComponents().remove(vevent);
        originalCalendar.getComponents().add(newVevent);

        this.adminConnector.putCalendar(uri, originalCalendar, OWNER);

        List<String> uris = new ArrayList<String>();
        uris.add(uri);
        List<Calendar> calendars = this.adminConnector.getCalendars(uris);
        assertFalse(calendars.isEmpty());
        Calendar newCalendar = calendars.get(0);
        VEvent updatedEvent = (VEvent) newCalendar.getComponent(Component.VEVENT);
        assertEquals(newSummary, updatedEvent.getSummary().getValue());
        assertEquals(new DateTime(newStart), updatedEvent.getStartDate().getDate());

    }

    @Test
    public void putTodo() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        Calendar calendar = buildVTodo(uuid);
        String uri = USER_HOME + uuid.toString() + ".ics";
        this.adminConnector.putCalendar(uri, calendar, OWNER);

        List<String> uris = new ArrayList<String>(1);
        uris.add(uri);
        List<Calendar> calendars = this.adminConnector.getCalendars(uris);
        Calendar calOnServer = calendars.get(0);
        VToDo vtodoOnServer = (VToDo) calOnServer.getComponent(Component.VTODO);
        VToDo originalVTodo = (VToDo) calendar.getComponent(Component.VTODO);

        assertEquals(originalVTodo.getDuration(), vtodoOnServer.getDuration());
        assertEquals(originalVTodo.getStartDate(), vtodoOnServer.getStartDate());
        assertEquals(originalVTodo.getDue(), vtodoOnServer.getDue());
        assertEquals(originalVTodo.getSummary(), vtodoOnServer.getSummary());
        assertEquals(originalVTodo.getUid(), vtodoOnServer.getUid());
        assertEquals(originalVTodo.getProperty(Property.CATEGORIES).getValue(),
                vtodoOnServer.getProperty(Property.CATEGORIES).getValue());
        assertEquals(CalDavConnector.MYBERKELEY_REQUIRED, vtodoOnServer.getProperty(Property.CATEGORIES).getValue());
    }

    private Calendar buildVTodo(UUID uuid) {
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
        calendar.getProperties().add(Version.VERSION_2_0);
        calendar.getProperties().add(CalScale.GREGORIAN);
        TimeZoneRegistry registry = builder.getRegistry();
        VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
        calendar.getComponents().add(tz);
        VToDo vtodo = new VToDo(new DateTime(), new DateTime(), "TODO created by admin " + uuid);
        vtodo.getProperties().add(new Uid(uuid.toString()));
        vtodo.getProperties().add(new Categories(CalDavConnector.MYBERKELEY_REQUIRED));
        calendar.getComponents().add(vtodo);
        return calendar;
    }

    private Calendar buildVevent(UUID uuid) {
        CalendarBuilder builder = new CalendarBuilder();
        Calendar c = new Calendar();
        c.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
        c.getProperties().add(Version.VERSION_2_0);
        c.getProperties().add(CalScale.GREGORIAN);
        TimeZoneRegistry registry = builder.getRegistry();
        VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
        c.getComponents().add(tz);
        String summary = "caldavtest uuid = " + uuid;
        VEvent vevent = new VEvent(new DateTime(),
                new Dur(0, 1, 0, 0), summary);
        vevent.getProperties().add(new Uid(uuid.toString()));
        c.getComponents().add(vevent);
        return c;
    }

    private boolean doesEntryExist(String uri) throws CalDavException {
        for (String thisURI : this.adminConnector.getAllUris()) {
            if ((SERVER_ROOT + thisURI).equals(uri)) {
                return true;
            }
        }
        return false;
    }

}
