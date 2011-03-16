package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Uid;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
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
    public void setup() throws CalDavException, URIException {
        this.adminConnector = new CalDavConnector("admin", "bedework", new URI(SERVER_ROOT, false), new URI(USER_HOME, false));
        this.userConnector = new CalDavConnector("vbede", "bedework", new URI(SERVER_ROOT, false), new URI(USER_HOME, false));
    }

    @Test
    public void deleteAll() throws CalDavException {
        List<CalendarUri> uris = this.adminConnector.getCalendarUris();
        for (CalendarUri uri : uris) {
            this.adminConnector.deleteCalendar(uri);
        }
        assertTrue(this.adminConnector.getCalendarUris().isEmpty());
    }

    @Test
    public void putCalendar() throws CalDavException {
        Calendar calendar = buildVevent("Created by CalDavTests");
        URI uri = this.adminConnector.putCalendar(calendar, OWNER);
        boolean found = doesEntryExist(uri);
        assertTrue(found);
    }

    @Test
    public void delete() throws CalDavException {
        Calendar calendar = buildVevent("Created by CalDavTests");
        URI uri = this.adminConnector.putCalendar(calendar, OWNER);
        assertTrue(doesEntryExist(uri));
        this.adminConnector.deleteCalendar(uri);
        assertFalse(doesEntryExist(uri));
    }

    @Test
    public void getCalendars() throws CalDavException {
        List<CalendarUri> uris = this.adminConnector.getCalendarUris();
        this.adminConnector.getCalendars(uris);
    }

    @Test
    public void putThenGetCalendarEntry() throws CalDavException, ParseException, URIException {
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        URI uri = this.adminConnector.putCalendar(originalCalendar, OWNER);

        List<CalendarUri> uris = new ArrayList<CalendarUri>();
        uris.add(new CalendarUri(uri, RANDOM_ETAG));
        List<CalendarWrapper> calendars = this.adminConnector.getCalendars(uris);
        assertFalse(calendars.isEmpty());

        Calendar calOnServer = calendars.get(0).getCalendar();
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
        URI uri = this.adminConnector.putCalendar(originalCalendar, OWNER);
        this.userConnector.deleteCalendar(uri);
    }

    @Test(expected = BadRequestException.class)
    public void verifyUserAbleToPutOwnEvent() throws CalDavException {
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        this.userConnector.putCalendar(originalCalendar, OWNER);
    }

    @Test
    public void putThenModify() throws CalDavException, ParseException, URIException {
        Calendar originalCalendar = buildVevent("Created by CalDavTests");
        URI uri = this.adminConnector.putCalendar(originalCalendar, OWNER);

        long newStart = System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 2);
        VEvent vevent = (VEvent) originalCalendar.getComponent(Component.VEVENT);
        String newSummary = "Updated event";
        VEvent newVevent = new VEvent(new DateTime(newStart),
                new Dur(0, 1, 0, 0), newSummary);
        newVevent.getProperties().add(new Uid(UUID.randomUUID().toString()));
        originalCalendar.getComponents().remove(vevent);
        originalCalendar.getComponents().add(newVevent);

        this.adminConnector.modifyCalendar(uri, originalCalendar, OWNER);

        List<CalendarUri> uris = new ArrayList<CalendarUri>();
        uris.add(new CalendarUri(uri, RANDOM_ETAG));
        List<CalendarWrapper> calendars = this.adminConnector.getCalendars(uris);
        assertFalse(calendars.isEmpty());
        Calendar newCalendar = calendars.get(0).getCalendar();
        VEvent updatedEvent = (VEvent) newCalendar.getComponent(Component.VEVENT);
        assertEquals(newSummary, updatedEvent.getSummary().getValue());
        assertEquals(new DateTime(newStart), updatedEvent.getStartDate().getDate());

    }

    @Test(expected = BadRequestException.class)
    public void modifyNonExistent() throws CalDavException, URIException, ParseException {
        Calendar calendar = buildVevent("Created by CalDavTests");
        URI uri = new URI(new URI(USER_HOME, false), "random-" + System.currentTimeMillis() + ".ics", false);

        this.adminConnector.modifyCalendar(uri, calendar, OWNER);
    }

    @Test
    public void putTodo() throws CalDavException, ParseException, URIException {
        Calendar calendar = buildVTodo("Todo created by CalDavTests");
        URI uri = this.adminConnector.putCalendar(calendar, OWNER);

        List<CalendarUri> uris = new ArrayList<CalendarUri>(1);
        uris.add(new CalendarUri(uri, RANDOM_ETAG));
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
        assertEquals(CalDavConnector.MYBERKELEY_REQUIRED, vtodoOnServer.getProperty(Property.CATEGORIES).getValue());
    }

    private boolean doesEntryExist(URI uri) throws CalDavException {
        for (CalendarUri thisURI : this.adminConnector.getCalendarUris()) {
            if ((thisURI.toString()).equals(uri.toString())) {
                return true;
            }
        }
        return false;
    }

}
