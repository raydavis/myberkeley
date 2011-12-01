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

import com.google.common.collect.Sets;

import edu.berkeley.myberkeley.api.provision.OaeAuthorizableService;
import edu.berkeley.myberkeley.api.provision.PersonAttributeProvider;
import edu.berkeley.myberkeley.api.provision.ProvisionResult;
import edu.berkeley.myberkeley.api.provision.SynchronizationState;

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
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet to let administrators provision CalCentral user accounts from an external source
 * (Oracle, currently).
 * A GET request will return the provided attributes of the specified users.
 * A POST request will create or update the CalCentral account.
 */
@SlingServlet(methods = { "GET", "POST" }, paths = {"/system/myberkeley/personProvision"},
    generateService = true, generateComponent = true)
public class PersonProvisionServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = 8837015974872136655L;
  public static final String SYSTEM_PROVIDED_USER_IDS_PARAM = "userIds";

  private static final Logger LOGGER = LoggerFactory.getLogger(PersonProvisionServlet.class);

  @Reference
  transient PersonAttributeProvider personAttributeProvider;
  @Reference
  transient OaeAuthorizableService oaeAuthorizableService;

  /**
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    // TODO Base on resource ACL.
    if (!"admin".equals(request.getRemoteUser())) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "YOU KIDS STAY OUT OF MY YARD!");
      return;
    }
    String[] personIds = request.getParameterValues(SYSTEM_PROVIDED_USER_IDS_PARAM);
    if (personIds == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing " + SYSTEM_PROVIDED_USER_IDS_PARAM + " parameter");
      return;
    }
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      ExtendedJSONWriter jsonWriter = new ExtendedJSONWriter(response.getWriter());
      jsonWriter.setTidy(Arrays.asList(request.getRequestPathInfo().getSelectors()).contains("tidy"));
      jsonWriter.object();
      jsonWriter.key("results");
      jsonWriter.array();
      for (String personId : personIds) {
        Map<String, Object> personAttributes = personAttributeProvider.getPersonAttributes(personId);
        LOGGER.info("ID = {}, attributes = {}", personId, personAttributes);
        ExtendedJSONWriter.writeValueMap(jsonWriter, personAttributes);
      }
      jsonWriter.endArray();
      jsonWriter.endObject();
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  /**
   * @see org.apache.sling.api.servlets.SlingAllMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    // TODO Base on resource ACL.
    if (!"admin".equals(request.getRemoteUser())) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "YOU KIDS STAY OUT OF MY YARD!");
      return;
    }
    String[] personIds = request.getParameterValues(SYSTEM_PROVIDED_USER_IDS_PARAM);
    if (personIds == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing " + SYSTEM_PROVIDED_USER_IDS_PARAM + " parameter");
      return;
    }
    Set<ProvisionResult> results = Sets.newHashSet();
    for (String personId : personIds) {
      Map<String, Object> personAttributes = personAttributeProvider.getPersonAttributes(personId);
      final ProvisionResult result;
      if (personAttributes != null) {
        result = oaeAuthorizableService.loadUser(personId, personAttributes);
      } else {
        result = new ProvisionResult(null, SynchronizationState.error);
      }
      results.add(result);
    }
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      ExtendedJSONWriter jsonWriter = new ExtendedJSONWriter(response.getWriter());
      jsonWriter.setTidy(Arrays.asList(request.getRequestPathInfo().getSelectors()).contains("tidy"));
      jsonWriter.object();
      jsonWriter.key("results");
      jsonWriter.array();
      for (ProvisionResult result : results) {
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
      }
      jsonWriter.endArray();
      jsonWriter.endObject();
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

}
