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
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.sakaiproject.nakamura.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

public class CalendarWrapperTest extends CalDavTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarWrapperTest.class);

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
    }

    @Test
    public void toJSON() throws URIException, ParseException, CalDavException, JSONException {
        CalendarWrapper wrapper = getWrapper();
        LOGGER.info("JSON = " + wrapper.toJSON());
    }

    @Test(expected = CalDavException.class)
    public void badCallToFromJSON() throws CalDavException, JSONException {
        JSONObject bogus = new JSONObject();
        bogus.put("foo", "bar");
        CalendarWrapper.fromJSON(bogus);
    }

    @Test
    public void vtodoFromJSON() throws CalDavException, IOException, JSONException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("calendarWrapper_vtodo.json");
        String json = IOUtils.readFully(in, "utf-8");
        JSONObject jsonObject = new JSONObject(json);

        CalendarWrapper wrapper = CalendarWrapper.fromJSON(jsonObject);
        assertNotNull(wrapper);
        assertEquals(wrapper.getComponent().getName(), Component.VTODO);
        LOGGER.info("Calendar wrapper after reading in from JSON: " + wrapper.toJSON().toString(2));

        // check for nondestructive deserialization
        assertEquals(wrapper, CalendarWrapper.fromJSON(wrapper.toJSON()));
    }

    @Test
    public void veventFromJSON() throws CalDavException, IOException, JSONException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("calendarWrapper_vevent.json");
        String json = IOUtils.readFully(in, "utf-8");
        JSONObject jsonObject = new JSONObject(json);

        CalendarWrapper wrapper = CalendarWrapper.fromJSON(jsonObject);
        assertNotNull(wrapper);
        assertEquals(wrapper.getComponent().getName(), Component.VEVENT);
        LOGGER.info("Calendar wrapper after reading in from JSON: " + wrapper.toJSON().toString(2));

        // check for nondestructive deserialization
        assertEquals(wrapper, CalendarWrapper.fromJSON(wrapper.toJSON()));
    }

    @Test
    @Ignore
    // TODO unignore when completed fromJSON method
    public void verifyFromJSONIdempotency() throws CalDavException, IOException, JSONException, ParseException {
        CalendarWrapper original = getWrapper();
        JSONObject json = original.toJSON();
        CalendarWrapper deserialized = CalendarWrapper.fromJSON(json);
        assertEquals(original, deserialized);
    }

    private CalendarWrapper getWrapper() throws URIException, ParseException, CalDavException {
        Calendar c = buildVTodo("a todo");
        URI uri = new URI("foo", false);
        return new CalendarWrapper(c, uri, RANDOM_ETAG);
    }
}

