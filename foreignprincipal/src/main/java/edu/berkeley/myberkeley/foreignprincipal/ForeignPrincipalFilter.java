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
package edu.berkeley.myberkeley.foreignprincipal;

import edu.berkeley.myberkeley.api.foreignprincipal.ForeignPrincipalService;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Service
@Component(immediate=true, metatype=true,
    label = "CalCentral :: Foreign Principal Request Filter", description = "Maintains and redirects externally authenticated non-members")
@Properties(value={@Property(name="service.description", value="Foreign Principal Filter"),
    @Property(name="service.vendor",value="The Sakai Foundation"),
    @Property(name="filter.scope",value="request", propertyPrivate=true),
    @Property(name="filter.order",intValue={10}, propertyPrivate=true)})
public class ForeignPrincipalFilter implements Filter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ForeignPrincipalFilter.class);
  public static final String EXTERNAL_AUTH_TYPE = "CAS";

  public static final String DEFAULT_FOREIGN_PRINCIPAL_HANDLER_PATH = "/index.html";
  @Property(value=DEFAULT_FOREIGN_PRINCIPAL_HANDLER_PATH,
      label = "Foreign Principal Handler Path", description = "Where to redirect externally authenticated non-members")
  public static final String FOREIGN_PRINCIPAL_HANDLER_PATH = "foreignprincipal.handler.path";
  private String foreignPrincipalHandlerPath;

  /**
   * We cannot reliably obtain a Sparse Session from the request while authentication
   * is underway, and so we need to obtain a Repository reference via OSGi.
   */
  @Reference
  Repository repository;

  @Reference
  ForeignPrincipalService foreignPrincipalService;

  /**
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  /**
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) resp;
    String authType = request.getAuthType();
    String remoteUser = request.getRemoteUser();
    Principal userPrincipal = request.getUserPrincipal();
    @SuppressWarnings("unchecked")
    ArrayList<String> attributeNames = Collections.list(request.getAttributeNames());
    LOGGER.debug("remoteUser = {}, authType = {}, userPrincipal = {}, attributes = {}",
        new Object[] {remoteUser, authType, userPrincipal, attributeNames});
    if (EXTERNAL_AUTH_TYPE.equals(authType)) {
      if (!isUserInLocalStorage(remoteUser)) {
        foreignPrincipalService.addForeignPrincipal(response, remoteUser);
        if (foreignPrincipalHandlerPath != null) {
          // Ask CAS to redirect to the external user handler rather than to the originally
          // requested URL.
          request.setAttribute(Authenticator.LOGIN_RESOURCE, foreignPrincipalHandlerPath);
        }
      }
    } else {
      remoteUser = foreignPrincipalService.getForeignPrincipal(request, response);
      LOGGER.info("Cookie remote user = {}", remoteUser);
    }
    chain.doFilter(req, resp);
  }

  /**
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
  }

  boolean isUserInLocalStorage(String userId) {
    boolean isLocal = false;
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      AuthorizableManager authMgr = adminSession.getAuthorizableManager();
      Authorizable authorizable = authMgr.findAuthorizable(userId);
      if (authorizable != null) {
        isLocal = true;
      } else {
        LOGGER.info("Authenticated principal {} does not exist in OAE storage", userId);
      }
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
    }
    return isLocal;
  }

  @Activate
  @Modified
  protected void activate(Map<?, ?> props) {
    foreignPrincipalHandlerPath = StringUtils.stripToNull(
        PropertiesUtil.toString(props.get(FOREIGN_PRINCIPAL_HANDLER_PATH), DEFAULT_FOREIGN_PRINCIPAL_HANDLER_PATH));
    LOGGER.debug("foreignPrincipalHandlerPath = {}", foreignPrincipalHandlerPath);
  }
}
