package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.Status;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
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
        end_date
    }

    public enum POST_PARAMS {
        uri,
        isArchived,
        isCompleted
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

        // TODO set the correct username instead of hardcoding vbede
        CalDavConnector connector = new CalDavConnector("vbede", "bedework",
                new URI("http://test.media.berkeley.edu:8080", false),
                new URI("http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/", false));

        CalendarSearchCriteria criteria = getCalendarSearchCriteria(request);

        try {
            handleGet(response, connector, criteria);
        } finally {
            response.getWriter().close();
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        // Keep out anon users.
        if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Anonymous users can't use the CalDAV Proxy Service.");
            return;
        }

        try {
            CalDavConnector connector = new CalDavConnector("admin", "bedework",
                    new URI("http://test.media.berkeley.edu:8080", false),
                    new URI("http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/", false));
            CalendarWrapper wrapper = getCalendarWrapper(request, connector);
            applyChangesToCalendar(request, connector, wrapper);
        } catch (Exception e) {
            LOGGER.error("Exception fetching calendar", e);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
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
        } catch (Exception e) {
            LOGGER.error("Exception fetching calendars", e);
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

    protected CalendarWrapper getCalendarWrapper(SlingHttpServletRequest request, CalDavConnector connector)
            throws CalDavException, IOException {
        RequestParameter uriParam = request.getRequestParameter(POST_PARAMS.uri.toString());
        CalendarURI uri = new CalendarURI(new URI(uriParam.toString(), false), new DateTime());
        List<CalendarWrapper> wrappers = connector.getCalendars(Arrays.asList(uri));
        return wrappers.get(0);
    }

    protected void applyChangesToCalendar(SlingHttpServletRequest request, CalDavConnector connector, CalendarWrapper wrapper)
            throws CalDavException, IOException {
        Component component = wrapper.getCalendar().getComponent(Component.VEVENT);
        if (component == null) {
            component = wrapper.getCalendar().getComponent(Component.VTODO);
        }
        component.getProperties().remove(CalDavConnector.MYBERKELEY_ARCHIVED);

        RequestParameter isArchived = request.getRequestParameter(POST_PARAMS.isArchived.toString());
        if (isArchived != null && isArchived.toString().equals("true")) {
            component.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);
        }
        RequestParameter isCompleted = request.getRequestParameter(POST_PARAMS.isCompleted.toString());
        if (isCompleted != null && isCompleted.toString().equals("true")) {
            component.getProperties().remove(Property.STATUS);
            component.getProperties().add(new Status(Status.COMPLETED));
        }

        // TODO set the correct owner of this calendar instead of hardcoding vbede
        connector.modifyCalendar(wrapper.getUri(), wrapper.getCalendar(), "vbede");
    }

}
