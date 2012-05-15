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
package org.sakaiproject.nakamura.accountprovider;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.sakaiproject.nakamura.api.accountprovider.OaeAuthorizableService;
import org.sakaiproject.nakamura.api.accountprovider.ProvisionResult;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet to support user account creation on test and demo systems without connecting to other systems.
 * Only the admin account is permitted to create users directly from request parameters.
 */
@SlingServlet(methods = { "POST" }, paths = {"/system/accountProvider/parameters"},
    generateService = true, generateComponent = true)
public class ParameterizedProvisionServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = 8837015974872136655L;
  public static final String CALLER_PROVIDED_USER_ID_PARAM = "userId";

  private static final Logger LOGGER = LoggerFactory.getLogger(ParameterizedProvisionServlet.class);

  @Reference
  transient OaeAuthorizableService oaeAuthorizableService;

  /**
   * @see org.apache.sling.api.servlets.SlingAllMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    String userId = request.getParameter(CALLER_PROVIDED_USER_ID_PARAM);
    if (userId == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing " + CALLER_PROVIDED_USER_ID_PARAM + " parameter");
      return;
    }
    if (!"admin".equals(request.getRemoteUser())) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
      return;
    }
    ProvisionResult result = createFromRequestParameters(userId, request);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      ExtendedJSONWriter jsonWriter = new ExtendedJSONWriter(response.getWriter());
      jsonWriter.setTidy(Arrays.asList(request.getRequestPathInfo().getSelectors()).contains("tidy"));
      jsonWriter.object();
      jsonWriter.key("synchronizationState");
      jsonWriter.value(result.getSynchronizationState().toString());
      jsonWriter.key("user");
      User user = result.getUser();
      if (user != null) {
        LOGGER.info("ID = {}, properties = {}", user.getId(), user.getOriginalProperties());
        ExtendedJSONWriter.writeValueMap(jsonWriter, user.getOriginalProperties());
      } else {
        jsonWriter.value(null);
      }
      jsonWriter.endObject();
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private ProvisionResult createFromRequestParameters(String userId, SlingHttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Map<String, String[]> requestParameters = request.getParameterMap();
    Map<String, Object> personAttributes = Maps.newHashMap();
    for (Entry<String, String[]> parameter : requestParameters.entrySet()) {
      final String key = parameter.getKey();
      if (!CALLER_PROVIDED_USER_ID_PARAM.equals(key)) {
        final String[] values = parameter.getValue();
        final Object value;
        if (values.length == 0) {
          value = null;
        } else if (values.length == 1) {
          value = values[0];
        } else {
          value = ImmutableSet.copyOf(values);
        }
        personAttributes.put(key, value);
      }
    }
    return oaeAuthorizableService.loadUser(userId, personAttributes);
  }

}
