package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Uid;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.time.DateUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This test is really an integration test used to exercise (and learn) CalDAV functionality.
 * It assumes test.media.berkeley.edu is up and running Bedework on port 8080.
 * If Bedework isn't running, the tests will still pass, although they won't be as informative.
 */

public class CalDavConnectorTest extends CalDavTests {

    private static final String OWNER = "mtwain";

    private static final String SERVER_ROOT = "http://test.media.berkeley.edu:8080";

    private static final String USER_HOME = SERVER_ROOT + "/ucaldav/user/" + OWNER + "/calendar/";

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CalDavConnectorTest.class);

    private CalDavConnector adminConnector;

    private CalDavConnector userConnector;

    @Before
    public void setup() throws CalDavException, URIException {
        this.adminConnector = new CalDavConnector("admin", "bedework", new URI(SERVER_ROOT, false), new URI(USER_HOME, false));
        this.userConnector = new CalDavConnector(OWNER, "bedework", new URI(SERVER_ROOT, false), new URI(USER_HOME, false));
        deleteAll();
    }

    @After
    public void cleanup() throws CalDavException {
        deleteAll();
    }

    @Test
    public void deleteAll() throws CalDavException {
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

    @Test
    public void putCalendar() throws CalDavException {
        try {
            Calendar calendar = buildVevent("Created by CalDavTests");
            URI uri = this.adminConnector.putCalendar(calendar, OWNER);
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
            CalendarURI uri = this.adminConnector.putCalendar(calendar, OWNER);
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
            URI uri = this.adminConnector.putCalendar(originalCalendar, OWNER);

            List<CalendarURI> uris = new ArrayList<CalendarURI>();
            uris.add(new CalendarURI(uri, RANDOM_ETAG));
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
        } catch (IOException ioe) {
            LOGGER.error("Trouble contacting server", ioe);
        }

    }

    @Test
    public void searchForEvent() throws CalDavException {
        try {
            deleteAll();

            Calendar originalCalendar = buildVevent("Created by CalDavTests");
            this.adminConnector.putCalendar(originalCalendar, OWNER);

            DateTime monthAgo = new DateTime(DateUtils.addDays(new Date(), -30));
            DateTime fourWeeksHence = new DateTime(DateUtils.addDays(new Date(), 28));

            // search for event just created, should find it
            CalendarSearchCriteria criteria = new CalendarSearchCriteria(
                    CalendarSearchCriteria.TYPE.VEVENT, monthAgo, fourWeeksHence, CalendarSearchCriteria.MODE.UNREQUIRED);
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
            CalendarURI todoURI = this.adminConnector.putCalendar(vtodo, OWNER);
            DateTime monthAgo = new DateTime(DateUtils.addDays(new Date(), -30));
            DateTime fourWeeksHence = new DateTime(DateUtils.addDays(new Date(), 28));

            CalendarSearchCriteria criteria = new CalendarSearchCriteria(
                    CalendarSearchCriteria.TYPE.VEVENT, monthAgo, fourWeeksHence, CalendarSearchCriteria.MODE.UNREQUIRED);
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
            this.adminConnector.modifyCalendar(todoURI, vtodo, OWNER);

            assertTrue(this.adminConnector.searchByDate(criteria).isEmpty());

            criteria.setMode(CalendarSearchCriteria.MODE.ALL_ARCHIVED);
            assertFalse(this.adminConnector.searchByDate(criteria).isEmpty());
        } catch (IOException ioe) {
            LOGGER.error("Trouble contacting server", ioe);
        }

    }

    @Test(expected = BadRequestException.class)
    public void verifyUserUnableToPutAnAdminCreatedEvent() throws CalDavException {
        try {
            Calendar originalCalendar = buildVevent("Created by CalDavTests");
            this.adminConnector.putCalendar(originalCalendar, OWNER);
            this.userConnector.putCalendar(originalCalendar, OWNER);
        } catch (IOException ioe) {
            LOGGER.error("Trouble contacting server", ioe);
            throw new BadRequestException("");
        }
    }

    @Test(expected = BadRequestException.class)
    public void verifyUserUnableToDelete() throws CalDavException {
        try {
            Calendar originalCalendar = buildVevent("Created by CalDavTests");
            CalendarURI uri = this.adminConnector.putCalendar(originalCalendar, OWNER);
            this.userConnector.deleteCalendar(uri);
        } catch (IOException ioe) {
            LOGGER.error("Trouble contacting server", ioe);
            throw new BadRequestException("");
        }
    }

    @Test(expected = BadRequestException.class)
    public void verifyUserAbleToPutOwnEvent() throws CalDavException {
        try {
            Calendar originalCalendar = buildVevent("Created by CalDavTests");
            this.userConnector.putCalendar(originalCalendar, OWNER);
        } catch (IOException ioe) {
            LOGGER.error("Trouble contacting server", ioe);
            throw new BadRequestException("");
        }
    }

    @Test
    public void putThenModify() throws CalDavException, ParseException, URIException {
        try {
            Calendar originalCalendar = buildVevent("Created by CalDavTests");
            CalendarURI uri = this.adminConnector.putCalendar(originalCalendar, OWNER);

            long newStart = System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 2);
            VEvent vevent = (VEvent) originalCalendar.getComponent(Component.VEVENT);
            String newSummary = "Updated event";
            VEvent newVevent = new VEvent(new DateTime(newStart),
                    new Dur(0, 1, 0, 0), newSummary);
            newVevent.getProperties().add(new Uid(UUID.randomUUID().toString()));
            originalCalendar.getComponents().remove(vevent);
            originalCalendar.getComponents().add(newVevent);

            this.adminConnector.modifyCalendar(uri, originalCalendar, OWNER);

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
    public void modifyNonExistent() throws CalDavException, URIException, ParseException {
        try {
            Calendar calendar = buildVevent("Created by CalDavTests");
            CalendarURI uri = new CalendarURI(
                    new URI(new URI(USER_HOME, false), "random-" + System.currentTimeMillis() + ".ics", false),
                    new DateTime());

            this.adminConnector.modifyCalendar(uri, calendar, OWNER);
        } catch (IOException ioe) {
            LOGGER.error("Trouble contacting server", ioe);
            throw new BadRequestException("");
        }
    }

    @Test
    public void putTodo() throws CalDavException, ParseException, URIException {
        try {
            Calendar calendar = buildVTodo("Todo created by CalDavTests");
            URI uri = this.adminConnector.putCalendar(calendar, OWNER);

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

    private boolean doesEntryExist(URI uri) throws CalDavException, IOException {
        for (CalendarURI thisURI : this.adminConnector.getCalendarUris()) {
            if ((thisURI.toString()).equals(uri.toString())) {
                return true;
            }
        }
        return false;
    }

}
