package edu.berkeley.myberkeley.dynamiclist;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import org.apache.commons.lang.CharEncoding;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(extensions = {"json"}, generateComponent = true, generateService = true,
        methods = {"GET"},
        resourceTypes = {DynamicListService.DYNAMIC_LIST_RT, DynamicListService.DYNAMIC_LIST_STORE_RT}
)
public class DynamicListGetServlet extends SlingSafeMethodsServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListGetServlet.class);

  @Reference
  transient DynamicListService dynamicListService;

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
          throws ServletException, IOException {

    // digest the selectors to determine if we should send a tidy result
    // or if we need to traverse deeper into the tagged node.
    boolean isTidy = false;
    int depth = 0;
    String[] selectors = request.getRequestPathInfo().getSelectors();

    for (String sel : selectors) {
      if ("tidy".equals(sel)) {
        isTidy = true;
      } else if ("infinity".equals(sel)) {
        depth = -1;
      } else {
        // check if the selector is telling us the depth of detail to return
        Integer d = null;
        try {
          d = Integer.parseInt(sel);
        } catch (NumberFormatException ignored) {
          // NaN
        }
        if (d != null) {
          depth = d;
        }
      }
    }

    LOGGER.info("Get of dynamic list with depth=" + depth + " and tidy=" + isTidy);

    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
            javax.jcr.Session.class));
    Resource resource = request.getResource();
    Content content = resource.adaptTo(Content.class);

    JSONWriter writer = new JSONWriter(response.getWriter());
    writer.setTidy(isTidy);
    response.setContentType("application/json");
    response.setCharacterEncoding(CharEncoding.UTF_8);

    try {
      List<Content> lists = findLists(content, session.getContentManager());

      String resourceType = (String) content.getProperty("sling:resourceType");
      if (isStore(content)) {
        writer.object();
      }

      for (Content list : lists) {
        if (isStore(content)) {
          writer.key(list.getPath());
        }
        writer.object();
        writer.key("numusers");
        Collection<String> users = this.dynamicListService.getUserIdsForNode(list, session);
        writer.value(users.size());
        ExtendedJSONWriter.writeContentTreeToWriter(writer, list, true, depth);
        writer.endObject();
      }

      if (DynamicListService.DYNAMIC_LIST_STORE_RT.equals(resourceType)) {
        writer.endObject();
      }

    } catch (JSONException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (StorageClientException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (RepositoryException e) {
      LOGGER.error(e.getLocalizedMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      response.getWriter().close();
    }

  }

  private boolean isStore(Content node) {
    String resourceType = (String) node.getProperty("sling:resourceType");
    return DynamicListService.DYNAMIC_LIST_STORE_RT.equals(resourceType);
  }

  private List<Content> findLists(Content parent, ContentManager contentManager) throws StorageClientException {
    List<Content> nodes = new ArrayList<Content>();
    if (isStore(parent)) {
      Iterator<Content> lists = contentManager.listChildren(parent.getPath());
      while (lists.hasNext()) {
        Content list = lists.next();
        String resourceType = (String) list.getProperty("sling:resourceType");
        if (DynamicListService.DYNAMIC_LIST_RT.equals(resourceType)) {
          nodes.add(list);
        }
      }
    } else {
      nodes.add(parent);
    }
    return nodes;
  }

}
