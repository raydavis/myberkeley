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
import static edu.berkeley.myberkeley.api.dynamiclist.DynamicListService.DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT;

import com.google.common.collect.ImmutableMap;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(selectors = { "myb-demographic" }, methods = { "POST" }, resourceTypes = { "sakai/user-home" },
    generateService = true, generateComponent = true)
public class DynamicListSetPersonalDemographicServlet extends SlingAllMethodsServlet {
  private static final long serialVersionUID = -4518066958705380929L;
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListSetPersonalDemographicServlet.class);

  private static final String PERSONAL_DEMOGRAPHIC_STORE_NAME = "_myberkeley-demographic";

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    // Make sure we have reasonable parameters.
    RequestParameter[] demographicParameters = request.getRequestParameters(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP);
    if (demographicParameters == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
      "The " + DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP + " parameter must not be null");
      return;
    }
    Set<String> demographicSet = new HashSet<String>();
    for (RequestParameter parameter : demographicParameters) {
      demographicSet.add(parameter.toString());
    }
    String[] demographics = demographicSet.toArray(new String[demographicSet.size()]);

    Content home = request.getResource().adaptTo(Content.class);
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
        javax.jcr.Session.class));
    try {
      ContentManager contentManager = session.getContentManager();
      String storePath = StorageClientUtils.newPath(home.getPath(), PERSONAL_DEMOGRAPHIC_STORE_NAME);
      if (!contentManager.exists(storePath)) {
        contentManager.update(new Content(storePath, ImmutableMap.of(
            JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
            (Object) DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT)));
        List<AclModification> modifications = new ArrayList<AclModification>();
        AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
        AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
        AccessControlManager accessControlManager = session.getAccessControlManager();
        accessControlManager.setAcl(Security.ZONE_CONTENT, storePath, modifications.toArray(new AclModification[modifications.size()]));
      }
      Content content = contentManager.get(storePath);
      content.setProperty(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP, demographics);
      LOGGER.info("Set {} demographics to {}", home.getPath(), Arrays.asList(demographics));
      contentManager.update(content);
    } catch (StorageClientException e) {
      throw new ServletException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      throw new ServletException(e.getMessage(), e);
    }
  }

}
