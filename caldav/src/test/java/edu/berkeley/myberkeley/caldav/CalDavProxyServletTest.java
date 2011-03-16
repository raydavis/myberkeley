package edu.berkeley.myberkeley.caldav;

import org.apache.commons.httpclient.URI;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.user.UserConstants;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.*;

public class CalDavProxyServletTest extends CalDavTests {

    private CalDavProxyServlet servlet;

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
    public void handleGet() throws ServletException, IOException, CalDavException, ParseException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(request.getRemoteUser()).thenReturn(UserConstants.ADMIN_USERID);
        when(response.getWriter()).thenReturn(new PrintWriter(System.out));
        CalDavConnector connector = mock(CalDavConnector.class);
        List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();
        calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/url1", false)));
        calendars.add(new CalendarWrapper(buildVevent("Test 2"), new URI("/url2", false)));
        calendars.add(new CalendarWrapper(buildVTodo("Todo Test 3"), new URI("/url3", false)));
        when(connector.getCalendars(anyListOf(CalendarUri.class))).thenReturn(calendars);
        servlet.handleGet(response, connector);

    }

}
