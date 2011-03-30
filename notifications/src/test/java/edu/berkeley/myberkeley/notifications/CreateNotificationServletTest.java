package edu.berkeley.myberkeley.notifications;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.util.IOUtils;
import org.sakaiproject.nakamura.util.parameters.ContainerRequestParameter;

import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

public class CreateNotificationServletTest {

    private CreateNotificationServlet servlet;

    @Before
    public void setup() {
        this.servlet = new CreateNotificationServlet();
    }

    @Test
    public void badParam() throws ServletException, IOException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        this.servlet.doPost(request, response);
        verify(response).sendError(Mockito.eq(HttpServletResponse.SC_BAD_REQUEST),
                Mockito.anyString());
    }

    @Test
    public void doPost() throws ServletException, IOException {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        InputStream in = getClass().getClassLoader().getResourceAsStream("notification.json");
        String json = IOUtils.readFully(in, "utf-8");
        when(request.getRequestParameter(CreateNotificationServlet.POST_PARAMS.notification.toString())).thenReturn(
                new ContainerRequestParameter(json, "utf-8"));

        this.servlet.doPost(request, response);

    }
}
