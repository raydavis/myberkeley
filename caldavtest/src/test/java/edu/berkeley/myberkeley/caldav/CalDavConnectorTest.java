package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.report.*;
import junit.framework.Assert;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * This test is really an integration test used to exercise (and learn) CalDAV functionality.
 * It assumes test.media.berkeley.edu is up and running Bedework on port 8080.
 */

public class CalDavConnectorTest extends Assert {

    private static final String SERVER_ROOT = "http://test.media.berkeley.edu:8080/ucaldav/";

    private static final String USER_HOME = SERVER_ROOT + "user/vbede/calendar/";

    private CalDavConnector connector;

    @Before
    public void setup() {
        this.connector = new CalDavConnector("vbede", "bedework");
    }

    @Test
    public void getOptions() throws CalDavException {
        this.connector.getOptions(USER_HOME);
    }

    @Test
    public void getCalendarHrefs() throws CalDavException {
        List<String> hrefs = this.connector.getCalendarHrefs(USER_HOME);
        assertFalse(hrefs.isEmpty());
    }

    @Test
    @Ignore
    public void putCalendar() throws CalDavException, IOException {

        List<String> hrefsBefore = this.connector.getCalendarHrefs(USER_HOME);

        UUID uuid = UUID.randomUUID();
        CalendarBuilder builder = new CalendarBuilder();
        net.fortuna.ical4j.model.Calendar c = new net.fortuna.ical4j.model.Calendar();
        c.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
        c.getProperties().add(Version.VERSION_2_0);
        c.getProperties().add(CalScale.GREGORIAN);
        TimeZoneRegistry registry = builder.getRegistry();
        VTimeZone tz = registry.getTimeZone("Europe/Madrid").getVTimeZone();
        c.getComponents().add(tz);
        String summary = "caldavtest uuid = " + uuid;
        VEvent vevent = new VEvent(new net.fortuna.ical4j.model.Date(),
                new Dur(0, 1, 0, 0), summary);
        vevent.getProperties().add(new Uid(uuid.toString()));
        c.getComponents().add(vevent);
        String href = USER_HOME + uuid.toString() + ".ics";

        CalDavConnector connector = new CalDavConnector("vbede", "bedework");
        connector.putCalendar(href, c);

        List<String> hrefsAfter = this.connector.getCalendarHrefs(USER_HOME);
        assertTrue(hrefsAfter.size() == hrefsBefore.size() + 1);
        String lastHref = hrefsAfter.get(hrefsAfter.size() - 1);
        assertEquals(href, lastHref);
    }

    @Test
    @Ignore
    public void doReport() throws IOException, DavException, ParserException {
        RequestCalendarData calendarData = new RequestCalendarData();
        Filter filter = new Filter("VCALENDAR");
        filter.getCompFilter().add(new Filter("VEVENT"));
        Calendar start = Calendar.getInstance();
        start.add(Calendar.MONTH, -1);
        Calendar end = Calendar.getInstance();
        filter.getCompFilter().get(0).setTimeRange(
                new TimeRange(start.getTime(), end.getTime()));
        ReportInfo reportInfo = new CalendarQueryReportInfo(calendarData, filter);
        this.connector.doReport(USER_HOME, reportInfo);
    }
}
