package edu.berkeley.myberkeley.dynamiclist;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import org.apache.commons.lang.CharEncoding;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
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
  private static final long serialVersionUID = 4960320583375004661L;
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListGetServlet.class);

  @Reference
  transient DynamicListService dynamicListService;

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
          throws ServletException, IOException {

    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
            javax.jcr.Session.class));
    Resource resource = request.getResource();
    Content content = resource.adaptTo(Content.class);

    JSONWriter writer = new JSONWriter(response.getWriter());
    writer.setTidy(isTidy(request));

    int page = 0;
    int items = 10;

    RequestParameter pageParam = request.getRequestParameter("page");
    if (pageParam != null) {
      try {
        page = Integer.valueOf(pageParam.toString());
      } catch (NumberFormatException ignored) {

      }
    }

    RequestParameter itemsParam = request.getRequestParameter("items");
    if (itemsParam != null) {
      try {
        items = Integer.valueOf(itemsParam.toString());
      } catch (NumberFormatException ignored) {

      }
    }

    response.setContentType("application/json");
    response.setCharacterEncoding(CharEncoding.UTF_8);

    try {
      List<Content> lists = new ArrayList<Content>();
      int total = findLists(lists, content, session.getContentManager(), page, items);

      if (isStore(content)) {
        writer.object();
        writer.key("total");
        writer.value(total);
      }

      for (Content list : lists) {
        if (isStore(content)) {
          writer.key(StorageClientUtils.getObjectName(list.getPath()));
        }
        writer.object();
        writer.key("numusers");
        Collection<String> users = this.dynamicListService.getUserIdsForNode(list, session);
        writer.value(users.size());
        ExtendedJSONWriter.writeContentTreeToWriter(writer, list, true, 1);
        writer.endObject();
      }

      if (isStore(content)) {
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

  private boolean isTidy(SlingHttpServletRequest request) {
    String[] selectors = request.getRequestPathInfo().getSelectors();
    for (String sel : selectors) {
      if ("tidy".equals(sel)) {
        return true;
      }
    }
    return false;
  }

  private boolean isStore(Content node) {
    String resourceType = (String) node.getProperty("sling:resourceType");
    return DynamicListService.DYNAMIC_LIST_STORE_RT.equals(resourceType);
  }

  private int findLists(List<Content> nodes, Content parent, ContentManager contentManager, int page, int items) throws StorageClientException {
    int totalLists = 0;
    if (isStore(parent)) {
      int currentPage = 0;
      int listsSeen = 0;
      Iterator<Content> lists = contentManager.listChildren(parent.getPath());
      while (lists.hasNext() ) {
        Content list = lists.next();
        String resourceType = (String) list.getProperty("sling:resourceType");
        if (DynamicListService.DYNAMIC_LIST_RT.equals(resourceType)) {
          listsSeen++;
          totalLists++;
          if ( currentPage == page && nodes.size() < items ) {
            nodes.add(list);
          }
        }
        if ( listsSeen % items == 0 ) {
          currentPage++;
        }
      }
    } else {
      nodes.add(parent);
    }
    return totalLists;
  }

}
