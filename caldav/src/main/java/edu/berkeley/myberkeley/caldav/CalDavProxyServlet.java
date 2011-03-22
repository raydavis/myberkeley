package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.Status;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@Service(value = Servlet.class)
@SlingServlet(paths = {"/system/myberkeley/caldav"}, methods = {"GET", "POST"}, generateComponent = true, generateService = true)

public class CalDavProxyServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavProxyServlet.class);

    public enum REQUEST_PARAMS {
        type,
        mode,
        start_date,
        end_date,
        json
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        // Keep out anon users.
        if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Anonymous users can't use the CalDAV Proxy Service.");
            return;
        }

        CalDavConnector connector = getConnector();

        CalendarSearchCriteria criteria = getCalendarSearchCriteria(request);

        try {
            handleGet(response, connector, criteria);
        } finally {
            response.getWriter().close();
        }
    }

    private CalDavConnector getConnector() throws URIException {
        return new CalDavConnector("vbede", "bedework",
                new URI("http://test.media.berkeley.edu:8080", false),
                new URI("http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/", false));
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        // Keep out anon users.
        if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Anonymous users can't use the CalDAV Proxy Service.");
            return;
        }

        CalendarWrapper wrapper = null;
        CalDavConnector connector = getConnector();


        try {
            JSONObject json = getJSONData(request);
            getCalendarWrapper(connector, json);
        } catch (JSONException je) {
            throw new ServletException("JSON parse error", je);
        } catch (CalDavException cde) {
            LOGGER.error("Exception fetching calendars", cde);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        try {
            handlePost(response, wrapper);
        } finally {
            response.getWriter().close();
        }
    }

    protected CalendarSearchCriteria getCalendarSearchCriteria(SlingHttpServletRequest request) throws ServletException {
        Date defaultStart = new DateTime();
        Date defaultEnd = new DateTime();
        CalendarSearchCriteria criteria = new CalendarSearchCriteria(CalendarSearchCriteria.TYPE.VEVENT,
                defaultStart, defaultEnd, CalendarSearchCriteria.MODE.ALL_UNARCHIVED);

        // apply non-default values from request if they're available

        RequestParameter type = request.getRequestParameter(REQUEST_PARAMS.type.toString());
        if (type != null) {
            criteria.setType(CalendarSearchCriteria.TYPE.valueOf(type.getString()));
        }
        RequestParameter mode = request.getRequestParameter(REQUEST_PARAMS.mode.toString());
        if (mode != null) {
            criteria.setMode(CalendarSearchCriteria.MODE.valueOf(mode.getString()));
        }
        RequestParameter startDate = request.getRequestParameter(REQUEST_PARAMS.start_date.toString());
        if (startDate != null) {
            try {
                criteria.setStart(new DateTime(startDate.getString()));
            } catch (ParseException pe) {
                throw new ServletException("Invalid start date passed: " + startDate.getString(), pe);
            }
        }
        RequestParameter endDate = request.getRequestParameter(REQUEST_PARAMS.end_date.toString());
        if (endDate != null) {
            try {
                criteria.setEnd(new DateTime(endDate.getString()));
            } catch (ParseException pe) {
                throw new ServletException("Invalid end date passed: " + endDate.getString(), pe);
            }
        }

        return criteria;
    }

    protected void handleGet(SlingHttpServletResponse response, CalDavConnector connector,
                             CalendarSearchCriteria criteria) throws IOException {
        List<CalendarWrapper> calendars;
        boolean hasOverdue;

        try {
            calendars = connector.searchByDate(criteria);
            hasOverdue = connector.hasOverdueTasks();
        } catch (CalDavException cde) {
            LOGGER.error("Exception fetching calendars", cde);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JSONObject json = new JSONObject();
            JSONArray results = new JSONArray();
            for (CalendarWrapper wrapper : calendars) {
                results.put(wrapper.toJSON());
            }
            json.put("results", results);
            json.put("hasOverdueTasks", hasOverdue);

            LOGGER.info("CalDavProxyServlet's JSON response: " + json.toString(2));
            response.getWriter().write(json.toString(2));
        } catch (JSONException je) {
            LOGGER.error("Failed to convert calendar to JSON", je);
        }

    }

    protected JSONObject getJSONData(SlingHttpServletRequest request) throws JSONException {
        RequestParameter jsonParam = request.getRequestParameter(REQUEST_PARAMS.json.toString());
        if (jsonParam != null) {
            JSONObject json = new JSONObject(jsonParam.toString());
            LOGGER.info("JSON data: " + json.toString(2));
            return json;
        }
        return null;
    }

    protected CalendarWrapper getCalendarWrapper(CalDavConnector connector, JSONObject json)
            throws JSONException, URIException, CalDavException {
        CalendarURI uri = new CalendarURI(new URI(json.getString("uri"), false), new DateTime());
        List<CalendarWrapper> wrappers = connector.getCalendars(Arrays.asList(uri));
        return wrappers.get(0);
    }

    protected void applyChangesToCalendar(CalDavConnector connector, JSONObject json, CalendarWrapper wrapper)
    throws JSONException, CalDavException, IOException {
        Component component = wrapper.getCalendar().getComponent(Component.VEVENT);
        if ( component == null ) {
            component = wrapper.getCalendar().getComponent(Component.VTODO);
        }
        component.getProperties().remove(CalDavConnector.MYBERKELEY_ARCHIVED);
        component.getProperties().remove(CalDavConnector.MYBERKELEY_REQUIRED);

        if ( json.getBoolean("isRequired")) {
            component.getProperties().add(CalDavConnector.MYBERKELEY_REQUIRED);
        }
        if ( json.getBoolean("isArchived")) {
            component.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);
        }
        if ( json.getBoolean("isCompleted")) {
            component.getProperties().remove(Property.STATUS);
            component.getProperties().add(new Status(Status.COMPLETED));
        }

        connector.modifyCalendar(wrapper.getUri(), wrapper.getCalendar(), "vbede");
    }

    protected void handlePost(SlingHttpServletResponse response, CalendarWrapper wrapper) throws IOException {

    }
}
