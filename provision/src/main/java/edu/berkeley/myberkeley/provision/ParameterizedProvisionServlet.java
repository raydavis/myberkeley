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
package edu.berkeley.myberkeley.provision;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.berkeley.myberkeley.api.provision.OaeAuthorizableService;

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
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet to support CalCentral user creation on test and demo systems with no Oracle connection.
 * Only the admin account is permitted to create CalCentral users directly from request parameters.
 */
@SlingServlet(methods = { "POST" }, paths = {"/system/myberkeley/testPersonProvision"},
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
    Set<User> users = Sets.newHashSet();
    String userId = request.getParameter(CALLER_PROVIDED_USER_ID_PARAM);
    if (userId != null) {
      if (!"admin".equals(request.getRemoteUser())) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "YOU KIDS STAY OUT OF MY YARD!");
        return;
      }
      User user = createFromRequestParameters(userId, request, response);
      users.add(user);
    }
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      ExtendedJSONWriter jsonWriter = new ExtendedJSONWriter(response.getWriter());
      jsonWriter.setTidy(Arrays.asList(request.getRequestPathInfo().getSelectors()).contains("tidy"));
      jsonWriter.object();
      jsonWriter.key("results");
      jsonWriter.array();
      for (User user : users) {
        LOGGER.info("ID = {}, properties = {}", user.getId(), user.getOriginalProperties());
        ExtendedJSONWriter.writeValueMap(jsonWriter, user.getOriginalProperties());
      }
      jsonWriter.endArray();
      jsonWriter.endObject();
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private User createFromRequestParameters(String userId, SlingHttpServletRequest request, SlingHttpServletResponse response) {
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
