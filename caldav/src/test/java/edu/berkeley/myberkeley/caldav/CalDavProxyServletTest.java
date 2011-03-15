package edu.berkeley.myberkeley.caldav;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.user.UserConstants;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

public class CalDavProxyServletTest extends Assert {

    private CalDavProxyServlet servlet;

    @Before
    public void setUp() throws Exception {
        this.servlet = new CalDavProxyServlet();
    }

    @Test
    public void testAnon() throws ServletException, IOException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);
        servlet.doGet(request, response);

        verify(response).sendError(Mockito.eq(HttpServletResponse.SC_UNAUTHORIZED),
                Mockito.anyString());
    }
}
