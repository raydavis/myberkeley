package edu.berkeley.myberkeley.dynamiclist;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import org.apache.commons.lang.CharEncoding;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(extensions = {"json"}, generateComponent = true, generateService = true,
        methods = {"GET"}, resourceTypes = {DynamicListService.DYNAMIC_LIST_RT}
)
public class DynamicListGetServlet extends SlingSafeMethodsServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListGetServlet.class);

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
          throws ServletException, IOException {

    // digest the selectors to determine if we should send a tidy result
    // or if we need to traverse deeper into the tagged node.
    boolean tidy = false;
    int depth = 0;
    String[] selectors = request.getRequestPathInfo().getSelectors();

    for (String sel : selectors) {
      if ("tidy".equals(sel)) {
        tidy = true;
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

    LOGGER.info("Get of dynamic list with depth=" + depth + " and tidy=" + tidy);

    Resource resource = request.getResource();
    Content listContent = resource.adaptTo(Content.class);

    JSONWriter writer = new JSONWriter(response.getWriter());
    writer.setTidy(tidy);

    response.setContentType("application/json");
    response.setCharacterEncoding(CharEncoding.UTF_8);

    try {
      writer.object();
      writer.key("numusers");
      writer.value(10);
      ExtendedJSONWriter.writeContentTreeToWriter(writer, listContent, true, depth);
      writer.endObject();
    } catch (JSONException je) {
      LOGGER.error(je.getLocalizedMessage(), je);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      response.getWriter().close();
    }

  }
}
