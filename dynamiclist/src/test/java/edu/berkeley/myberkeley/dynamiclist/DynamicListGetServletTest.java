package edu.berkeley.myberkeley.dynamiclist;

import static org.mockito.Mockito.when;
 import static org.mockito.Mockito.mock;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import javax.servlet.ServletException;

public class DynamicListGetServletTest {

  @Mock
  private SlingHttpServletRequest request;

  @Mock
  private SlingHttpServletResponse response;

  public DynamicListGetServletTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void infiniteGet() throws IOException, ServletException {
    DynamicListGetServlet servlet = new DynamicListGetServlet();

    RequestPathInfo pathInfo = mock(RequestPathInfo.class);
    when(pathInfo.getSelectors()).thenReturn(new String[] { "tidy", "infinity" });
    when(this.request.getRequestPathInfo()).thenReturn(pathInfo);

    servlet.doGet(this.request, this.response);
  }
}
