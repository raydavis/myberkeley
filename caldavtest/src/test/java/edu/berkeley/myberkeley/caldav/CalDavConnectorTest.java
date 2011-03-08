package edu.berkeley.myberkeley.caldav;

import junit.framework.Assert;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.junit.Before;
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

    private CalDavConnector connector;

    @Before
    public void setup() throws CalDavException {
        this.connector = new CalDavConnector("vbede", "bedework", USER_HOME);

        List<String> hrefs = this.connector.getCalendarHrefs();
        for (String href : hrefs) {
            this.connector.deleteCalendar(SERVER_ROOT + href);
        }
    }

    @Test
    public void getOptions() throws CalDavException {
        this.connector.getOptions();
    }

    @Test
    public void getCalendarHrefs() throws CalDavException {
        List<String> hrefs = this.connector.getCalendarHrefs();
        assertTrue(hrefs.isEmpty());
    }

    @Test
    public void putCalendar() throws CalDavException {

        List<String> hrefsBefore = this.connector.getCalendarHrefs();

        UUID uuid = UUID.randomUUID();
        String href = USER_HOME + uuid.toString() + ".ics";

        Calendar calendar = buildVevent(uuid);
        this.connector.putCalendar(href, calendar);

        List<String> hrefsAfter = this.connector.getCalendarHrefs();
        assertTrue(hrefsAfter.size() == hrefsBefore.size() + 1);

        boolean found = false;
        for (String thisHref : hrefsAfter) {
            if ((SERVER_ROOT + thisHref).equals(href)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void getCalendars() throws CalDavException {
        List<String> hrefs = this.connector.getCalendarHrefs();
        List<Calendar> calendars = this.connector.getCalendars(hrefs);
        assertTrue(calendars.isEmpty());
    }

    @Test
    public void putThenGetCalendarEntry() throws CalDavException {

        UUID uuid = UUID.randomUUID();
        String href = USER_HOME + uuid.toString() + ".ics";
        Calendar originalCalendar = buildVevent(uuid);
        this.connector.putCalendar(href, originalCalendar);

        List<String> hrefs = new ArrayList<String>();
        hrefs.add(href);
        List<Calendar> calendars = this.connector.getCalendars(hrefs);
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

    @Test
    public void putThenModify() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        String href = USER_HOME + uuid.toString() + ".ics";
        Calendar originalCalendar = buildVevent(uuid);
        this.connector.putCalendar(href, originalCalendar);

        VEvent vevent = (VEvent) originalCalendar.getComponent(Component.VEVENT);
        String newSummary = "Updated event";
        VEvent newVevent = new VEvent(new Date(1234L),
                new Dur(0, 1, 0, 0), newSummary);
        newVevent.getProperties().add(new Uid(uuid.toString()));
        originalCalendar.getComponents().remove(vevent);
        originalCalendar.getComponents().add(newVevent);

        this.connector.putCalendar(href, originalCalendar);

        List<String> hrefs = new ArrayList<String>();
        hrefs.add(href);
        List<Calendar> calendars = this.connector.getCalendars(hrefs);
        assertFalse(calendars.isEmpty());
        Calendar newCalendar = calendars.get(0);
        VEvent updatedEvent = (VEvent) newCalendar.getComponent(Component.VEVENT);
        assertEquals(newSummary, updatedEvent.getSummary().getValue());
        assertEquals(new Date(1234L), updatedEvent.getStartDate().getDate());
    }

    @Test
    public void putTodo() throws CalDavException {
        UUID uuid = UUID.randomUUID();
        Calendar calendar = buildVTodo(uuid);
        String href = USER_HOME + uuid.toString() + ".ics";
        this.connector.putCalendar(href, calendar);

        List<String> hrefs = new ArrayList<String>(1);
        hrefs.add(href);
        List<Calendar> calendars = this.connector.getCalendars(hrefs);
        Calendar calOnServer = calendars.get(0);
        VToDo vtodoOnServer = (VToDo) calOnServer.getComponent(Component.VTODO);
        VToDo originalVTodo = (VToDo) calendar.getComponent(Component.VTODO);

        assertEquals(originalVTodo.getDuration(), vtodoOnServer.getDuration());
        assertEquals(originalVTodo.getStartDate(), vtodoOnServer.getStartDate());
        assertEquals(originalVTodo.getDue(), vtodoOnServer.getDue());
        assertEquals(originalVTodo.getSummary(), vtodoOnServer.getSummary());
        assertEquals(originalVTodo.getUid(), vtodoOnServer.getUid());
    }

    private Calendar buildVTodo(UUID uuid) {
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = new Calendar();
        calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
        calendar.getProperties().add(Version.VERSION_2_0);
        calendar.getProperties().add(CalScale.GREGORIAN);
        TimeZoneRegistry registry = builder.getRegistry();
        VTimeZone tz = registry.getTimeZone("Europe/Madrid").getVTimeZone();
        calendar.getComponents().add(tz);
        VToDo vtodo = new VToDo(new Date(), new Date(), "Test TODO " + uuid);
        vtodo.getProperties().add(new Uid(uuid.toString()));
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
        VTimeZone tz = registry.getTimeZone("Europe/Madrid").getVTimeZone();
        c.getComponents().add(tz);
        String summary = "caldavtest uuid = " + uuid;
        VEvent vevent = new VEvent(new Date(),
                new Dur(0, 1, 0, 0), summary);
        vevent.getProperties().add(new Uid(uuid.toString()));
        c.getComponents().add(vevent);
        return c;
    }

}
