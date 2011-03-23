package edu.berkeley.myberkeley.caldav;

import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.httpclient.URI;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
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
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

public class CalDavPostServletTest extends CalDavTests {

    private CalDavPostServlet servlet;

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavGetServletTest.class);

    @Before
    public void setUp() throws Exception {
        this.servlet = new CalDavPostServlet();
    }

    @Test
    public void noAnonymousUsers() throws ServletException, IOException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);
        servlet.doPost(request, response);

        verify(response).sendError(Mockito.eq(HttpServletResponse.SC_UNAUTHORIZED),
                Mockito.anyString());
    }

    @Test
    public void getCalendarWrapper() throws JSONException, IOException, CalDavException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameter(CalDavPostServlet.POST_PARAMS.uri.toString())).thenReturn(
                new ContainerRequestParameter("/uri1", "utf-8"));


        List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();
        calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/url1", false), RANDOM_ETAG));

        CalDavConnector connector = mock(CalDavConnector.class);
        when(connector.getCalendars(anyList())).thenReturn(calendars);
        CalendarWrapper wrapper = servlet.getCalendarWrapper(request, connector);

        assertNotNull(wrapper);
    }

    @Test
    public void applyChangesToCalendar() throws JSONException, IOException, CalDavException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        when(request.getRequestParameter(CalDavPostServlet.POST_PARAMS.uri.toString())).thenReturn(
                new ContainerRequestParameter("/uri1", "utf-8"));
        when(request.getRequestParameter(CalDavPostServlet.POST_PARAMS.isArchived.toString())).thenReturn(
                        new ContainerRequestParameter("true", "utf-8"));
        when(request.getRequestParameter(CalDavPostServlet.POST_PARAMS.isCompleted.toString())).thenReturn(
                        new ContainerRequestParameter("true", "utf-8"));

        CalDavConnector connector = mock(CalDavConnector.class);

        CalendarWrapper wrapper = new CalendarWrapper(buildVevent("Test 1"), new URI("/url1", false), RANDOM_ETAG);
        JSONObject beforeChange = wrapper.toJSON();
        assertFalse(beforeChange.getBoolean("isCompleted"));
        assertFalse(beforeChange.getBoolean("isRequired"));
        assertFalse(beforeChange.getBoolean("isArchived"));

        servlet.applyChangesToCalendar(request, connector, wrapper);
        JSONObject afterChange = wrapper.toJSON();
        assertTrue(afterChange.getBoolean("isCompleted"));
        assertFalse(afterChange.getBoolean("isRequired"));
        assertTrue(afterChange.getBoolean("isArchived"));

    }
}
