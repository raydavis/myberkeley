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

import org.sakaiproject.nakamura.api.accountprovider.ForeignPrincipalService;
import org.sakaiproject.nakamura.api.accountprovider.OaeAuthorizableService;
import org.sakaiproject.nakamura.api.accountprovider.PersonAttributeProvider;
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet to support self-registration of externally authenticated users.
 * A GET request will return the provided attributes of the new user.
 * A POST request will create the user account.
 */
@SlingServlet(methods = { "GET", "POST" }, paths = {"/system/accountProvider/self"},
    generateService = true, generateComponent = true)
public class SelfProvisionServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = 8837015974872136655L;
  public static final String SYSTEM_PROVIDED_USER_IDS_PARAM = "userIds";

  private static final Logger LOGGER = LoggerFactory.getLogger(SelfProvisionServlet.class);

  @Reference
  transient PersonAttributeProvider personAttributeProvider;
  @Reference
  transient ForeignPrincipalService foreignPrincipalService;
  @Reference
  transient OaeAuthorizableService oaeAuthorizableService;

  /**
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest, org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    final String personId = foreignPrincipalService.getForeignPrincipal(request, response);
    if (personId == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown user");
      return;
    }
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      ExtendedJSONWriter jsonWriter = new ExtendedJSONWriter(response.getWriter());
      jsonWriter.setTidy(Arrays.asList(request.getRequestPathInfo().getSelectors()).contains("tidy"));
      if (personId != null) {
        Map<String, Object> personAttributes = personAttributeProvider.getPersonAttributes(personId);
        LOGGER.info("ID = {}, attributes = {}", personId, personAttributes);
        ExtendedJSONWriter.writeValueMap(jsonWriter, personAttributes);
      }
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
    final String personId = foreignPrincipalService.getForeignPrincipal(request, response);
    if (personId == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown user");
      return;
    }
    Map<String, Object> personAttributes = personAttributeProvider.getPersonAttributes(personId);
    ProvisionResult result = oaeAuthorizableService.loadUser(personId, personAttributes);
    // If the new user made the POST herself, then she is also agreeing to join as a participant.
    oaeAuthorizableService.initializeParticipant(personId);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      ExtendedJSONWriter jsonWriter = new ExtendedJSONWriter(response.getWriter());
      jsonWriter.setTidy(Arrays.asList(request.getRequestPathInfo().getSelectors()).contains("tidy"));
      User user = result.getUser();
      if (user != null) {
        LOGGER.info("ID = {}, properties = {}", user.getId(), user.getOriginalProperties());
        ExtendedJSONWriter.writeValueMap(jsonWriter, user.getOriginalProperties());
      } else {
        jsonWriter.value(null);
      }
    } catch (JSONException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

}
