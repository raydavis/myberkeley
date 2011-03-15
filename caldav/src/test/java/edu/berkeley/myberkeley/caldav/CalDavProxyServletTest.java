package edu.berkeley.myberkeley.caldav;

import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.fortuna.ical4j.model.Calendar;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.user.UserConstants;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

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
    public void handleGet() throws ServletException, IOException, CalDavException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(request.getRemoteUser()).thenReturn(UserConstants.ADMIN_USERID);
        when(response.getWriter()).thenReturn(new PrintWriter(System.out));
        CalDavConnector connector = mock(CalDavConnector.class);
        List<Calendar> calendars = new ArrayList<Calendar>();
        calendars.add(buildVevent("Test 1"));
        calendars.add(buildVevent("Test 2"));
        calendars.add(buildVTodo("Todo Test 3"));
        when(connector.getCalendars(anyListOf(String.class))).thenReturn(calendars);
        servlet.handleGet(response, connector);

    }

}
