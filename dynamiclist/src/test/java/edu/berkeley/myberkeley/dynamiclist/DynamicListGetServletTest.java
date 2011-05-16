package edu.berkeley.myberkeley.dynamiclist;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.testing.jcr.MockProperty;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;

public class DynamicListGetServletTest extends Assert {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListGetServletTest.class);

  private static final String LIST_PATH = "/my/list/path";

  private static final String CHILD_PATH = LIST_PATH + "/child";

  private static final String GRANDCHILD_PATH = CHILD_PATH + "/grandchild";

  private static final String QUERY_PATH = LIST_PATH + "/query";

  @Mock
  private SlingHttpServletRequest request;

  @Mock
  private SlingHttpServletResponse response;

  private ByteArrayOutputStream responseStream;

  private DynamicListGetServlet servlet;

  public DynamicListGetServletTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setupExpectations() throws AccessDeniedException, StorageClientException, IOException, ClassNotFoundException, RepositoryException {
    this.servlet = new DynamicListGetServlet();
    DynamicListSparseSolrImpl service = new DynamicListSparseSolrImpl();
    service.slingRepository = mock(SlingRepository.class);
    this.servlet.dynamicListService = service;

    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    Repository repository = baseMemoryRepository.getRepository();
    ContentManager contentManager = repository.loginAdministrative().getContentManager();

    Content content = new Content(LIST_PATH, ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) DynamicListService.DYNAMIC_LIST_RT));
    contentManager.update(content);

    Content child = new Content(CHILD_PATH, ImmutableMap.of(
            "child1prop1",
            (Object) "prop1val"));
    contentManager.update(child);

    Content grandchild = new Content(GRANDCHILD_PATH, ImmutableMap.of(
            "grandchildprop1",
            (Object) "prop1val"));
    contentManager.update(grandchild);

    Content query = new Content(QUERY_PATH, ImmutableMap.of(
            "grandchildprop1",
            (Object) "prop1val"));
    query.setProperty("filter", "some criteria");
    query.setProperty("context", "test-context");
    contentManager.update(query);

    // we have to get the content via contentmanager so that it gets properly set up with internalize(),
    // or else the ExtendedJSONWriter call will fail. Ian insists this is not a code smell.
    Content contentFromCM = contentManager.get(LIST_PATH);
    Resource resource = mock(Resource.class);
    when(request.getResource()).thenReturn(resource);
    when(resource.adaptTo(Content.class)).thenReturn(contentFromCM);

    ResourceResolver resolver = mock(ResourceResolver.class);
    when(this.request.getResourceResolver()).thenReturn(resolver);
    Session session = mock(Session.class);
    when(session.getContentManager()).thenReturn(contentManager);

    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);

    Node node = mock(Node.class);
    when(node.getProperty(DynamicListService.DYNAMIC_LIST_CONTEXT_PROP)).thenReturn(new MockProperty(DynamicListService.DYNAMIC_LIST_CONTEXT_PROP));
    when(service.slingRepository.loginAdministrative(null)).thenReturn(jcrSession);
    when(jcrSession.getNode(anyString())).thenReturn(node);

    this.responseStream = new ByteArrayOutputStream();
    when(response.getWriter()).thenReturn(new PrintWriter(this.responseStream));

  }

  @Test
  public void infiniteGet() throws IOException, ServletException, JSONException {
    RequestPathInfo pathInfo = mock(RequestPathInfo.class);
    when(pathInfo.getSelectors()).thenReturn(new String[]{"tidy", "infinity"});
    when(this.request.getRequestPathInfo()).thenReturn(pathInfo);

    this.servlet.doGet(this.request, this.response);

    JSONObject json = new JSONObject(responseStream.toString("utf-8"));
    LOGGER.info(json.toString(2));
    assertNotNull(json);
    assertNotNull(json.getJSONObject(CHILD_PATH));
    assertNotNull(json.getJSONObject(CHILD_PATH).getJSONObject(GRANDCHILD_PATH));

  }

  @Test(expected = JSONException.class)
  public void finiteGet() throws ServletException, IOException, JSONException {
    RequestPathInfo pathInfo = mock(RequestPathInfo.class);
    when(pathInfo.getSelectors()).thenReturn(new String[]{"1"});
    when(this.request.getRequestPathInfo()).thenReturn(pathInfo);

    this.servlet.doGet(this.request, this.response);

    JSONObject json = new JSONObject(responseStream.toString("utf-8"));
    LOGGER.info(json.toString(2));
    assertNotNull(json);
    assertNotNull(json.getJSONObject(CHILD_PATH));
    // next line should produce JSONException since grandchild isn't part of 1-level output
    json.getJSONObject(CHILD_PATH).getJSONObject(GRANDCHILD_PATH);
  }
}