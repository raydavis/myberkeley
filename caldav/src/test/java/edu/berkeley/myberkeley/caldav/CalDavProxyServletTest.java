package edu.berkeley.myberkeley.caldav;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.URI;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.parameters.ContainerRequestParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

public class CalDavProxyServletTest extends CalDavTests {

    private CalDavProxyServlet servlet;

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavProxyServletTest.class);

    @Before
    public void setUp() throws Exception {
        this.servlet = new CalDavProxyServlet();
    }

    @Test
    public void noAnonymousUsers() throws ServletException, IOException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);
        servlet.doGet(request, response);

        verify(response).sendError(Mockito.eq(HttpServletResponse.SC_UNAUTHORIZED),
                Mockito.anyString());
    }

    @Test
    public void adminUser() throws ServletException, IOException, JSONException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(request.getRemoteUser()).thenReturn(UserConstants.ADMIN_USERID);
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        when(response.getWriter()).thenReturn(new PrintWriter(responseStream));

        try {
            servlet.doGet(request, response);
            JSONObject json = new JSONObject(responseStream.toString("utf-8"));
            JSONArray results = (JSONArray) json.get("results");
            assertNotNull(results);
            Boolean hasOverdue = json.getBoolean("hasOverdueTasks");
            assertNotNull(hasOverdue);
        } catch ( IOException ioe ) {
            LOGGER.error("Trouble contacting bedework server", ioe);
        }
    }

    @Test
    public void handleGet() throws ServletException, IOException, CalDavException, ParseException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(request.getRemoteUser()).thenReturn(UserConstants.ADMIN_USERID);
        when(response.getWriter()).thenReturn(new PrintWriter(System.out));
        CalDavConnector connector = mock(CalDavConnector.class);
        List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();
        calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/url1", false), RANDOM_ETAG));
        calendars.add(new CalendarWrapper(buildVevent("Test 2"), new URI("/url2", false), RANDOM_ETAG));
        calendars.add(new CalendarWrapper(buildVTodo("Todo Test 3"), new URI("/url3", false), RANDOM_ETAG));
        Date defaultStart = new DateTime();
        Date defaultEnd = new DateTime();
        CalendarSearchCriteria criteria = new CalendarSearchCriteria(CalendarSearchCriteria.TYPE.VEVENT,
                defaultStart, defaultEnd, CalendarSearchCriteria.MODE.ALL_UNARCHIVED);

        when(connector.searchByDate(criteria)).thenReturn(calendars);
        servlet.handleGet(response, connector, criteria);

    }

    @Test
    public void getCalendarSearchCriteria() throws ServletException, ParseException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.type.toString())).thenReturn(
                new ContainerRequestParameter("VTODO", "utf-8"));
        when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.mode.toString())).thenReturn(
                new ContainerRequestParameter(CalendarSearchCriteria.MODE.ALL_ARCHIVED.toString(), "utf-8"));
        when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.start_date.toString())).thenReturn(
                new ContainerRequestParameter(RANDOM_ETAG, "utf-8"));
        when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.end_date.toString())).thenReturn(
                new ContainerRequestParameter(MONTH_AFTER_RANDOM_ETAG, "utf-8"));

        CalendarSearchCriteria criteria = servlet.getCalendarSearchCriteria(request);
        assertEquals(CalendarSearchCriteria.TYPE.VTODO, criteria.getType());
        assertEquals(CalendarSearchCriteria.MODE.ALL_ARCHIVED, criteria.getMode());
        assertEquals(new DateTime(RANDOM_ETAG, "yyyyMMdd'T'HHmmss", true), criteria.getStart());
        assertEquals(new DateTime(MONTH_AFTER_RANDOM_ETAG, "yyyyMMdd'T'HHmmss", true), criteria.getEnd());
    }

    @Test
    public void getDefaultSearchCriteria() throws ServletException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        servlet.getCalendarSearchCriteria(request);
    }

    @Test(expected = ServletException.class)
    public void bogusStartDate() throws ServletException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.start_date.toString())).thenReturn(
                new ContainerRequestParameter("not a date", "utf-8"));

        servlet.getCalendarSearchCriteria(request);
    }

    @Test(expected = ServletException.class)
    public void bogusEndDate() throws ServletException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.end_date.toString())).thenReturn(
                new ContainerRequestParameter("not a date either", "utf-8"));

        servlet.getCalendarSearchCriteria(request);
    }
}
