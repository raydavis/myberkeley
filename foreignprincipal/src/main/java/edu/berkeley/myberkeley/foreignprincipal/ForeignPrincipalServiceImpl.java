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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component(metatype = true, policy = ConfigurationPolicy.REQUIRE,
    label = "CalCentral :: Foreign Principal Service", description = "Support external authentication of non-members")
@Service
public class ForeignPrincipalServiceImpl implements ForeignPrincipalService {
  public static final String FOREIGN_PRINCIPAL_KEY = "foreignprincipal";
  private static final Logger LOGGER = LoggerFactory.getLogger(ForeignPrincipalServiceImpl.class);
  private static final long TIME_TO_LIVE_MS = 7200000;

  @Property(label = "Secret encryption key", description = "Must be set to enable self-registration")
  public static final String FOREIGN_PRINCIPAL_SECRET_ = "foreignprincipal.secret";
  private String secretKey;

  /**
   * @see edu.berkeley.myberkeley.api.foreignprincipal.ForeignPrincipalService#addForeignPrincipal(javax.servlet.http.HttpServletResponse, java.lang.String)
   */
  @Override
  public void addForeignPrincipal(HttpServletResponse response, String userId) {
    CookieUtils.addCookie(response, FOREIGN_PRINCIPAL_KEY, userId, secretKey, TIME_TO_LIVE_MS);
  }

  /**
   * @see edu.berkeley.myberkeley.api.foreignprincipal.ForeignPrincipalService#getForeignPrincipal(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  public String getForeignPrincipal(HttpServletRequest request, HttpServletResponse response) {
    String userId = (String) request.getAttribute(FOREIGN_PRINCIPAL_KEY);
    if (userId == null) {
      userId = CookieUtils.getPayload(request, response, FOREIGN_PRINCIPAL_KEY, secretKey);
      request.setAttribute(FOREIGN_PRINCIPAL_KEY, userId);
    }
    return userId;
  }

  /**
   * @see edu.berkeley.myberkeley.api.foreignprincipal.ForeignPrincipalService#dropForeignPrincipal(javax.servlet.http.HttpServletResponse)
   */
  @Override
  public void dropForeignPrincipal(HttpServletResponse response) {
    CookieUtils.clearCookie(response, FOREIGN_PRINCIPAL_KEY);
  }

  @Activate
  @Modified
  protected void activate(Map<?, ?> props) {
    secretKey = PropertiesUtil.toString(props.get(FOREIGN_PRINCIPAL_SECRET_), null);
    LOGGER.debug("secretKey = {}", secretKey);
  }
}
