package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Version;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.junit.Test;

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
    public void isCompletedArchivedAndRequired() throws URIException, ParseException, CalDavException{
        Calendar calendar = buildVTodo("completed task");
        Component todo = calendar.getComponent(Component.VTODO);
        todo.getProperties().add(Status.VTODO_COMPLETED);
        todo.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
        todo.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);

        URI uri = new URI("foo", false);
        CalendarWrapper wrapper = new CalendarWrapper(calendar, uri, RANDOM_ETAG);
        assertTrue(wrapper.isCompleted());
        assertTrue(wrapper.isArchived());
        assertTrue(wrapper.isRequired());
    }

}
