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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.auth.core.spi.AuthenticationHandler;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handler to catch dropCredentials so that any local Foreign Principal cookie can be dropped.
 */
@Component(metatype = true)
@Service
@Properties(value = {
    @Property(name = AuthenticationHandler.PATH_PROPERTY, value = "/"),
    @Property(name = AuthenticationHandler.TYPE_PROPERTY, value = ForeignPrincipalAuthenticationHandler.AUTH_TYPE, propertyPrivate = true)
})
public class ForeignPrincipalAuthenticationHandler implements AuthenticationHandler {
  public static final String AUTH_TYPE = "foreignprincipal";
  private static final Logger LOGGER = LoggerFactory.getLogger(ForeignPrincipalAuthenticationHandler.class);

  @Reference
  ForeignPrincipalService foreignPrincipalService;

  /**
   * @see org.apache.sling.auth.core.spi.AuthenticationHandler#dropCredentials(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  public void dropCredentials(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    LOGGER.debug("dropCredentials");
    foreignPrincipalService.dropForeignPrincipal(response);
  }

  /**
   * @see org.apache.sling.auth.core.spi.AuthenticationHandler#extractCredentials(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  public AuthenticationInfo extractCredentials(HttpServletRequest request,
      HttpServletResponse response) {
    return null;
  }

  /**
   * @see org.apache.sling.auth.core.spi.AuthenticationHandler#requestCredentials(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  public boolean requestCredentials(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    return false;
  }
}
