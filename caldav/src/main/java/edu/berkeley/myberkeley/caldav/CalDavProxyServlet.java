package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.DateProperty;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

        CalDavConnector connector = new CalDavConnector("vbede", "bedework",
                new URI("http://test.media.berkeley.edu:8080", false),
                new URI("http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/", false));
        handleGet(response, connector);

    }

    protected void handleGet(SlingHttpServletResponse response, CalDavConnector connector) throws IOException {
        List<CalendarWrapper> calendars;

        try {
            List<CalendarWrapper.CalendarUri> calendarUris = connector.getCalendarUris();
            calendars = connector.getCalendars(calendarUris);
        } catch (CalDavException cde) {
            LOGGER.error("Exception fetching calendars", cde);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JSONObject json = toJSON(calendars);
            LOGGER.info("CalDavProxyServlet's JSON response: " + json.toString(2));
            response.getWriter().write(json.toString());
        } catch (JSONException je) {
            LOGGER.error("Failed to convert calendar to JSON", je);
        }

    }

    private JSONObject toJSON(List<CalendarWrapper> calendars) throws JSONException {
        JSONObject obj = new JSONObject();

        JSONObject events = new JSONObject();
        for (CalendarWrapper wrapper : calendars) {
            JSONObject thisEvent = new JSONObject();
            ComponentList vevents = wrapper.getCalendar().getComponents(Component.VEVENT);
            for (Object vevent : vevents) {
                writeCalendarComponent(wrapper, (VEvent) vevent, thisEvent);
                events.put(wrapper.getUri().toString(), thisEvent);
            }
        }

        JSONObject todos = new JSONObject();
        for (CalendarWrapper wrapper : calendars) {
            JSONObject thisTodo = new JSONObject();
            ComponentList vtodos = wrapper.getCalendar().getComponents(Component.VTODO);
            for (Object todo : vtodos) {
                writeCalendarComponent(wrapper, (VToDo) todo, thisTodo);
                todos.put(wrapper.getUri().toString(), thisTodo);
            }
        }

        obj.put("vevents", events);
        obj.put("vtodos", todos);

        return obj;
    }

    private void writeCalendarComponent(CalendarWrapper wrapper, CalendarComponent calendarComponent, JSONObject obj) throws JSONException {
        obj.put("ETAG", DateUtils.iso8601(wrapper.getEtag()));
        PropertyList propertyList = calendarComponent.getProperties();
        for (Object prop : propertyList) {
            Property property = (Property) prop;
            // Check if it is a date
            String value = property.getValue();
            if (property instanceof DateProperty) {
                DateProperty start = (DateProperty) property;
                value = DateUtils.iso8601(start.getDate());
            }
            obj.put(property.getName(), value);
        }
    }
}
