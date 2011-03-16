package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.DateProperty;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONStringer;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service(value = Servlet.class)
@SlingServlet(paths = {"/system/myberkeley/caldav"}, methods = {"GET"}, generateComponent = true, generateService = true)

public class CalDavProxyServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavProxyServlet.class);

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        // Keep out anon users.
        if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Anonymous users can't use the CalDAV Proxy Service.");
            return;
        }

        CalDavConnector connector = new CalDavConnector("vbede", "bedework", "http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/");
        handleGet(response, connector);

    }

    protected void handleGet(SlingHttpServletResponse response, CalDavConnector connector) throws IOException {
        List<CalendarWrapper> calendars;

        try {
            List<CalendarUri> calendarUris = connector.getCalendarUris();
            List<String> uriStrings = new ArrayList<String>(calendarUris.size());
            for (CalendarUri uri : calendarUris) {
                uriStrings.add(uri.getUri());
            }
            calendars = connector.getCalendars(uriStrings);
        } catch (CalDavException cde) {
            LOGGER.error("Exception fetching calendars", cde);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONStringer write = new JSONStringer();
        write.setTidy(true);

        try {
            write.object();

            // VEvents
            write.key("vevents");
            write.array();
            for (CalendarWrapper wrapper : calendars) {
                ComponentList vevents = wrapper.getCalendar().getComponents(Component.VEVENT);
                for (Object vevent : vevents) {
                    writeCalendarComponent((VEvent) vevent, write);
                }
            }
            write.endArray();

            // VTodos
            write.key("vtodos");
            write.array();
            for (CalendarWrapper wrapper : calendars) {
                ComponentList vtodos = wrapper.getCalendar().getComponents(Component.VTODO);
                for (Object vtodo : vtodos) {
                    writeCalendarComponent((VToDo) vtodo, write);
                }
            }
            write.endArray();

            write.endObject();

        } catch (JSONException je) {
            LOGGER.error("Failed to convert calendar to JSON", je);
        }

        LOGGER.info("CalDavProxyServlet's JSON response: " + write.toString());
        response.getWriter().write(write.toString());

    }

    private void writeCalendarComponent(CalendarComponent calendarComponent, JSONWriter write) throws JSONException {
        write.object();
        PropertyList pList = calendarComponent.getProperties();
        int i = 0;
        int size = pList.size();
        while (i < size) {
            net.fortuna.ical4j.model.Property p = (net.fortuna.ical4j.model.Property) pList
                    .get(i);
            write.key(p.getName());
            // Check if it is a date
            String value = p.getValue();
            if (p instanceof DateProperty) {
                DateProperty start = (DateProperty) p;
                value = DateUtils.iso8601(start.getDate());
            }

            write.value(value);
            i++;
        }
        write.endObject();
    }
}
