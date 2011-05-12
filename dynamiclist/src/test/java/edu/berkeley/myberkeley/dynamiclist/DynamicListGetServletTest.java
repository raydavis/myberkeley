package edu.berkeley.myberkeley.dynamiclist;

import static org.mockito.Mockito.when;
 import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.content.ContentManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.servlet.ServletException;

public class DynamicListGetServletTest extends Assert {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListGetServletTest.class);

  @Mock
  private SlingHttpServletRequest request;

  @Mock
  private SlingHttpServletResponse response;

  public DynamicListGetServletTest() {
    MockitoAnnotations.initMocks(this);
  }

  // commented out until someone fixes ExtendedJSONWriter.writeContentTreeToWriter()
  // see https://jira.sakaiproject.org/browse/KERN-1863
  /*
  @Test
  public void infiniteGet() throws IOException, ServletException, StorageClientException, JSONException {
    DynamicListGetServlet servlet = new DynamicListGetServlet();

    RequestPathInfo pathInfo = mock(RequestPathInfo.class);
    when(pathInfo.getSelectors()).thenReturn(new String[] { "tidy", "infinity" });
    when(this.request.getRequestPathInfo()).thenReturn(pathInfo);

    Content content = new Content("/my/list/path", ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) DynamicListService.DYNAMIC_LIST_RT));

    ContentManagerImpl cm = mock(ContentManagerImpl.class);
    content.internalize(cm, false);

    Content child1 = new Content("/my/list/path/child1", ImmutableMap.of(
            "child1prop1",
            (Object) "prop1val"));
    child1.internalize(cm, false);
    List<Content> children = new ArrayList<Content>();
    children.add(child1);

    when(cm.listChildren("/my/list/path")).thenReturn(children.iterator());
    when(cm.listChildren("/my/list/path/child1")).thenReturn(new ArrayList<Content>().iterator());

    Resource resource = mock(Resource.class);
    when(request.getResource()).thenReturn(resource);
    when(resource.adaptTo(Content.class)).thenReturn(content);

    ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
    when(response.getWriter()).thenReturn(new PrintWriter(responseStream));

    servlet.doGet(this.request, this.response);

    JSONObject json = new JSONObject(responseStream.toString("utf-8"));
    assertNotNull(json);
    LOGGER.info(json.toString(2));
  }
  */
}