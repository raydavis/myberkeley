package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Uid;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This test is really an integration test used to exercise (and learn) CalDAV functionality.
 * It assumes test.media.berkeley.edu is up and running Bedework on port 8080.
 */

public class CalDavConnectorTest extends CalDavTests {

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
    public void deleteAll() throws CalDavException {
        List<CalendarUri> uris = this.adminConnector.getAllUris();
        for (CalendarUri uri : uris) {
            this.adminConnector.deleteCalendar(SERVER_ROOT + uri.getUri());
        }
    }

    @Test
    public void putCalendar() throws CalDavException {
        Calendar calendar = buildVevent("Created by CalDavTests");
        String uri = this.adminConnector.putCalendar(calendar, OWNER);
        boolean found = doesEntryExist(uri);
        assertTrue(found);
    }

    @Test
    public void delete() throws CalDavException {
        Calendar calendar = buildVevent("Created by CalDavTests");
        String uri = this.adminConnector.putCalendar(calendar, OWNER);
        assertTrue(doesEntryExist(uri));
        this.adminConnector.deleteCalendar(uri);
        assertFalse(doesEntryExist(uri));
    }

    @Test
    public void getCalendars() throws CalDavException {
        List<CalendarUri> uris = this.adminConnector.getAllUris();
        List<String> uriStrings = new ArrayList<String>(uris.size());
        for ( CalendarUri uri : uris ) {
            uriStrings.add(uri.getUri());
        }
        this.adminConnector.getCalendars(uriStrings);
    }

    @Test
    public void putThenGetCalendarEntry() throws CalDavException {
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        String uri = this.adminConnector.putCalendar(originalCalendar, OWNER);

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
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        this.adminConnector.putCalendar(originalCalendar, OWNER);
        this.userConnector.putCalendar(originalCalendar, OWNER);
    }

    @Test(expected = BadRequestException.class)
    public void verifyUserUnableToDelete() throws CalDavException {
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        String uri = this.adminConnector.putCalendar(originalCalendar, OWNER);
        this.userConnector.deleteCalendar(uri);
    }

    @Test(expected = BadRequestException.class)
    public void verifyUserAbleToPutOwnEvent() throws CalDavException {
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        this.userConnector.putCalendar(originalCalendar, OWNER);
    }

    @Test
    public void putThenModify() throws CalDavException {
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        String uri = this.adminConnector.putCalendar(originalCalendar, OWNER);

        long newStart = System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 2);
        VEvent vevent = (VEvent) originalCalendar.getComponent(Component.VEVENT);
        String newSummary = "Updated event";
        VEvent newVevent = new VEvent(new DateTime(newStart),
                new Dur(0, 1, 0, 0), newSummary);
        newVevent.getProperties().add(new Uid(UUID.randomUUID().toString()));
        originalCalendar.getComponents().remove(vevent);
        originalCalendar.getComponents().add(newVevent);

        this.adminConnector.modifyCalendar(uri, originalCalendar, OWNER);

        List<String> uris = new ArrayList<String>();
        uris.add(uri);
        List<Calendar> calendars = this.adminConnector.getCalendars(uris);
        assertFalse(calendars.isEmpty());
        Calendar newCalendar = calendars.get(0);
        VEvent updatedEvent = (VEvent) newCalendar.getComponent(Component.VEVENT);
        assertEquals(newSummary, updatedEvent.getSummary().getValue());
        assertEquals(new DateTime(newStart), updatedEvent.getStartDate().getDate());

    }

    @Test(expected = BadRequestException.class)
    public void modifyNonExistent() throws CalDavException {
        Calendar calendar = buildVevent("Created by CalDavTests");
        String uri = USER_HOME + "random-" + System.currentTimeMillis() + ".ics";
        this.adminConnector.modifyCalendar(uri, calendar, OWNER);
    }

    @Test
    public void putTodo() throws CalDavException {
        Calendar calendar = buildVTodo("Todo created by CalDavTests");
        String uri = this.adminConnector.putCalendar(calendar, OWNER);

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

    private boolean doesEntryExist(String uri) throws CalDavException {
        for (CalendarUri thisURI : this.adminConnector.getAllUris()) {
            if ((SERVER_ROOT + thisURI.getUri()).equals(uri)) {
                return true;
            }
        }
        return false;
    }

}
