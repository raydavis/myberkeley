/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package edu.berkeley.myberkeley.dynamiclist;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListContext;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(extensions = {"json"}, methods = {"GET", "POST"},
        resourceTypes = {DynamicListService.DYNAMIC_LIST_CONTEXT_RT},
        generateComponent = true, generateService = true)
public class DynamicListQueryServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = -4638092585830025716L;
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListQueryServlet.class);
  @Reference
  protected transient DynamicListService dynamicListService;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    handleRequest(request, response);
  }

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
          throws ServletException, IOException {
    handleRequest(request, response);
  }

  private void handleRequest(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
    // Get the Dynamic List Context from the target resource.
    DynamicListContext context;
    Resource resource = request.getResource();
    Node node = resource.adaptTo(Node.class);
    if (node != null) {
      try {
        context = new DynamicListContext(node);
      } catch (RepositoryException e) {
        LOGGER.warn(e.getMessage(), e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to find Dynamic List Context.");
        return;
      }
    } else {
      LOGGER.warn("DynamicListQuery called against non-JCR Resource {}", resource.getPath());
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to find Dynamic List Context.");
      return;
    }

    // Get the criteria as a JSON string. This parameter is required!
    String criteria = request.getRequestParameter("criteria").getString();

    // WARNING: Dynamic List search bypasses normal access restrictions for the user session.
    // The raw user IDs returned by the Dynamic List service must not be included in
    // the servlet response, and all search criteria must be vetted for the currently
    // authenticated user.
    Collection<String> userIds = dynamicListService.getUserIdsForCriteria(context, criteria);
    LOGGER.info("For criteria = {} and context = {}, user Ids = {}", new Object[] {criteria, context.getContextId(), userIds});

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    JSONWriter write = new JSONWriter(response.getWriter());
    try {
      write.object().key("count").value(userIds.size()).endObject();
    } catch (JSONException e) {
      LOGGER.warn(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create the proper JSON structure.");
    }
  }

}
