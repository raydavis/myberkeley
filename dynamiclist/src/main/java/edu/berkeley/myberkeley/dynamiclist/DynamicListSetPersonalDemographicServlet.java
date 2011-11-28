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

import static edu.berkeley.myberkeley.api.dynamiclist.DynamicListService.DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.post.SlingPostConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Create or update a hidden demographics profile beneath the targeted User home folder.
 * This profile will then be indexed by Solr and later searched by the DynamicList service.
 */
@SlingServlet(selectors = { "myb-demographic" }, methods = { "POST" }, resourceTypes = { "sakai/user-home" },
    generateService = true, generateComponent = true)
public class DynamicListSetPersonalDemographicServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = -4518066958705380929L;

  @Reference
  protected transient DynamicListService dynamicListService;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    Set<String> demographicSet;
    boolean isClear = isDeleteRequest(request);
    if (!isClear) {
      demographicSet = new HashSet<String>();
      RequestParameter[] demographicParameters = request.getRequestParameters(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP);
      if (demographicParameters == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
        "The " + DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP + " parameter must not be null");
        return;
      }
      for (RequestParameter parameter : demographicParameters) {
        demographicSet.add(parameter.toString());
      }
    } else {
      demographicSet = null;
    }
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
        javax.jcr.Session.class));
    final String path = request.getResource().getPath();
    final String userId = path.replaceFirst(LitePersonalUtils.PATH_RESOURCE_AUTHORIZABLE, "");
    try {
      dynamicListService.setDemographics(session, userId, demographicSet);
    } catch (StorageClientException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      throw new ServletException(e.getMessage(), e);
    }
  }

  private boolean isDeleteRequest(SlingHttpServletRequest request) {
    RequestParameter requestParameter = request.getRequestParameter(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP + SlingPostConstants.SUFFIX_DELETE);
    return (requestParameter != null);
  }

}
