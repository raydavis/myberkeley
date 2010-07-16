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
package edu.berkeley.myberkeley.root.impl;

import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;

import edu.berkeley.myberkeley.root.ApplicationRootService;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 *
 */
@Component(metatype = true, immediate = true)
public class ApplicationRootServiceImpl implements ApplicationRootService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRootServiceImpl.class);

  @Property(value="", description="The path to which the root URL should redirect. Sling default is '/index.html'. Empty string means no change.")
  static final String ROOT_PATH = "root.path";
  @Property(value="", description="The password of the built-in Jackrabbit admin user. Empty string means no change.")
  static final String ADMIN_PASSWORD = "admin.password";

  protected String rootPath;
  protected String adminPassword;

  @Reference
  protected transient SlingRepository repository;

  //----------- OSGi integration ----------------------------

  @Activate
  protected void activate(Map<?, ?> properties) {
    init(properties);
  }

  @Modified
  protected void modified(Map<?, ?> properties) {
    init(properties);
  }

  //----------- Internal ----------------------------

  private void init(Map<?, ?> properties) {
    rootPath = OsgiUtil.toString(properties.get(ROOT_PATH), "");
    if (rootPath.length() > 0) {
      setRootPath(rootPath);
    }
    adminPassword = OsgiUtil.toString(properties.get(ADMIN_PASSWORD), "");
    if (adminPassword.length() > 0) {
      setAdminPassword(adminPassword);
    }
  }

  private void setRootPath(String path) {
    Session session = null;
    try {
      session = repository.loginAdministrative(null);
      Node rootNode = session.getNode("/");
      String resourceType = null;
      if (rootNode.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)) {
        resourceType = rootNode.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString();
      }
      if (!PROP_SLING_REDIRECT_TYPE.equals(resourceType)) {
        LOGGER.warn("Asked to set a non-redirecting root node to {}", path);
      } else {
        String oldRootPath = null;
        if (rootNode.hasProperty(PROP_SLING_REDIRECT_TARGET)) {
          javax.jcr.Property redirectTarget = rootNode.getProperty(PROP_SLING_REDIRECT_TARGET);
          oldRootPath = redirectTarget.getString();
        }
        if (!path.equals(oldRootPath)) {
          LOGGER.info("Changing root path from {} to {}", oldRootPath, path);
          rootNode.setProperty(PROP_SLING_REDIRECT_TARGET, path);
          session.save();
        }
      }
    } catch (RepositoryException e) {
      LOGGER.error("Error setting root path to " + path, e);
    } finally {
      if (session != null) {
        session.logout();
      }
    }
  }

  private void setAdminPassword(String password) {
    JackrabbitSession session = null;
    try {
      session = (JackrabbitSession) repository.loginAdministrative(null);
      UserManager userManager = session.getUserManager();
      User admin = (User) userManager.getAuthorizable("admin");
      if (admin != null) {
        LOGGER.info("Changing admin password");
        admin.changePassword(password);
      } else {
        LOGGER.warn("Could not find admin user to change password");
      }
    } catch (RepositoryException e) {
      LOGGER.error("Error setting admin password", e);
    } finally {
      if (session != null) {
        session.logout();
      }
    }
  }
}
