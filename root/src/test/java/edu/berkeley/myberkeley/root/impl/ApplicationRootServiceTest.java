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

import static edu.berkeley.myberkeley.root.ApplicationRootService.PROP_SLING_REDIRECT_TARGET;
import static edu.berkeley.myberkeley.root.ApplicationRootService.PROP_SLING_REDIRECT_TYPE;
import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ApplicationRootServiceTest {
  private ApplicationRootServiceImpl applicationRootService;

  @Mock
  SlingRepository repository;
  @Mock
  private JackrabbitSession session;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Node rootNode;
  @Mock
  UserManager userManager;
  @Mock
  User adminUser;

  @Before
  public void setup() throws Exception {
    when(repository.loginAdministrative(null)).thenReturn(session);
    when(session.getNode("/")).thenReturn(rootNode);
    when(rootNode.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)).thenReturn(true);
    when(session.getUserManager()).thenReturn(userManager);
    when(userManager.getAuthorizable("admin")).thenReturn(adminUser);
    applicationRootService = new ApplicationRootServiceImpl();
    applicationRootService.repository = repository;
  }

  @Test
  public void doNothingIfBlankProperties() throws RepositoryException {
    applicationRootService.activate(new HashMap<String, String>());
    verify(repository, never()).loginAdministrative(null);
    verify(adminUser, never()).changePassword(anyString());
  }

  @Test
  public void changeRedirectPath() throws RepositoryException {
    when(rootNode.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString()).thenReturn(PROP_SLING_REDIRECT_TYPE);
    HashMap<String, String> props = new HashMap<String, String>();
    props.put(ApplicationRootServiceImpl.ROOT_PATH, "/joe");
    applicationRootService.activate(props);
    verify(rootNode).setProperty(PROP_SLING_REDIRECT_TARGET, "/joe");
  }

  @Test
  public void doNothingIfSameRedirectPath() throws RepositoryException {
    when(rootNode.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString()).thenReturn(PROP_SLING_REDIRECT_TYPE);
    when(rootNode.hasProperty(PROP_SLING_REDIRECT_TARGET)).thenReturn(true);
    when(rootNode.getProperty(PROP_SLING_REDIRECT_TARGET).getString()).thenReturn("/joe");
    HashMap<String, String> props = new HashMap<String, String>();
    props.put(ApplicationRootServiceImpl.ROOT_PATH, "/joe");
    applicationRootService.activate(props);
    verify(rootNode, never()).setProperty(eq(PROP_SLING_REDIRECT_TARGET), anyString());
  }

  @Test
  public void doNothingIfNotRedirectNode() throws RepositoryException {
    when(rootNode.getProperty(SLING_RESOURCE_TYPE_PROPERTY).getString()).thenReturn("wrong:one");
    HashMap<String, String> props = new HashMap<String, String>();
    props.put(ApplicationRootServiceImpl.ROOT_PATH, "/joe");
    applicationRootService.activate(props);
    verify(rootNode, never()).setProperty(eq(PROP_SLING_REDIRECT_TARGET), anyString());
  }

  @Test
  public void changeAdminPassword() throws RepositoryException {
    HashMap<String, String> props = new HashMap<String, String>();
    props.put(ApplicationRootServiceImpl.ADMIN_PASSWORD, "sekrit");
    applicationRootService.activate(props);
    verify(adminUser).changePassword("sekrit");
  }

}
