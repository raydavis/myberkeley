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
package edu.berkeley.myberkeley.foreignprincipal.impl;

import static edu.berkeley.myberkeley.foreignprincipal.ForeignPrincipal.DYNAMIC_PRINCIPAL_PROPERTY;
import static edu.berkeley.myberkeley.foreignprincipal.ForeignPrincipal.FOREIGN_PRINCIPAL_ID;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.jackrabbit.server.security.dynamic.DynamicPrincipalManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

/**
 * Associate a dynamic Jackrabbit Group with authenticated users who do not yet have a matching
 * Jackrabbit Authorizable record. This lets ACLs include "all externally-hosted users."
 */
@Component(metatype = true, immediate = true)
@Service
public class ForeignPrincipalManagerImpl implements DynamicPrincipalManager {
  private static Logger LOGGER = LoggerFactory.getLogger(ForeignPrincipalManagerImpl.class);

  @Reference
  protected transient SlingRepository repository;

  public boolean hasPrincipalInContext(String principalName, Node aclNode,
      Node contextNode, String userId) {
    try {
      LOGGER.info("hasPrincipalInContxt {}, {}, {}, {}", new Object[] {principalName, aclNode.getPath(), contextNode.getPath(), userId});
      if (FOREIGN_PRINCIPAL_ID.equals(principalName)) {
        JackrabbitSession session = (JackrabbitSession) aclNode.getSession();
        UserManager userManager = session.getUserManager();
        Authorizable authorizable = userManager.getAuthorizable(userId);
        if (authorizable == null) {
          LOGGER.info("User {} not found, returning true");
          return true;
        }
      }
    } catch (RepositoryException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return false;
  }

  public List<String> getMembersOf(String principalName) {
    LOGGER.info("getMembersOf {}", principalName);
    return null;
  }

  public List<String> getMembershipFor(String principalName) {
    LOGGER.info("getMembershipFor {}", principalName);
    return null;
  }

  //----------- OSGi integration ----------------------------

  @Activate
  protected void activate(Map<?, ?> properties) {
    ensureForeignAuthorizable();
  }

  //----------- Internal ----------------------------
  private void ensureForeignAuthorizable() {
    Authorizable foreigners = null;
    JackrabbitSession session = null;
    try {
      session = (JackrabbitSession) repository.loginAdministrative(null);
      UserManager userManager = session.getUserManager();
      foreigners = userManager.getAuthorizable(FOREIGN_PRINCIPAL_ID);
      if (foreigners == null) {
        LOGGER.info("Will create group for dynamic principal {}", FOREIGN_PRINCIPAL_ID);
        Principal principal = getForeignPrincipal();
        ValueFactory valueFactory = session.getValueFactory();
        foreigners = userManager.createGroup(principal);
        foreigners.setProperty(DYNAMIC_PRINCIPAL_PROPERTY, valueFactory.createValue("true"));
      }
    } catch (RepositoryException e) {
      LOGGER.error("Exception in ensureAuthorizable", e);
    } finally {
      if (session != null) {
        session.logout();
      }
    }
  }

  private Principal getForeignPrincipal() {
    Principal foreignPrincipal = new Principal() {
      public String getName() {
        return FOREIGN_PRINCIPAL_ID;
      }
    };
    return foreignPrincipal;
  }
}
