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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.security.Principal;
import java.util.HashMap;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

@RunWith(MockitoJUnitRunner.class)
public class ForeignPrincipalTest {
  private ForeignPrincipalManagerImpl foreignPrincipalManager;

  @Mock
  SlingRepository repository;
  @Mock
  private JackrabbitSession session;
  @Mock
  ValueFactory valueFactory;
  @Mock
  UserManager userManager;
  @Mock
  User adminUser;
  @Mock
  Group foreignGroup;

  @Before
  public void setup() throws Exception {
    when(repository.loginAdministrative(null)).thenReturn(session);
    when(session.getValueFactory()).thenReturn(valueFactory);
    when(session.getUserManager()).thenReturn(userManager);
    when(userManager.getAuthorizable("admin")).thenReturn(adminUser);
    foreignPrincipalManager = new ForeignPrincipalManagerImpl();
    foreignPrincipalManager.repository = repository;
  }

  @Test
  public void handlesForeignPrincipals() throws RepositoryException {
    Node node = mock(Node.class, RETURNS_DEEP_STUBS);
    when(node.getSession()).thenReturn(session);
    when(userManager.getAuthorizable(FOREIGN_PRINCIPAL_ID)).thenReturn(foreignGroup);
    boolean isForeign = foreignPrincipalManager.hasPrincipalInContext(FOREIGN_PRINCIPAL_ID, node, node, "stranger");
    assertTrue(isForeign);
  }

  @Test
  public void ignoresLocalPrincipals() throws RepositoryException {
    Node node = mock(Node.class, RETURNS_DEEP_STUBS);
    when(node.getSession()).thenReturn(session);
    when(userManager.getAuthorizable(FOREIGN_PRINCIPAL_ID)).thenReturn(foreignGroup);
    User user = mock(User.class);
    when(userManager.getAuthorizable("homebody")).thenReturn(user);
    boolean isForeign = foreignPrincipalManager.hasPrincipalInContext(FOREIGN_PRINCIPAL_ID, node, node, "homebody");
    assertFalse(isForeign);
  }

  @Test
  public void ensuresDynamicAuthorizable() throws RepositoryException {
    when(userManager.createGroup(any(Principal.class))).thenReturn(foreignGroup);
    ArgumentCaptor<Principal> principal = ArgumentCaptor.forClass(Principal.class);
    foreignPrincipalManager.activate(new HashMap<String, String>());
    verify(userManager).createGroup(principal.capture());
    assertEquals(FOREIGN_PRINCIPAL_ID, principal.getValue().getName());
    verify(foreignGroup).setProperty(eq(DYNAMIC_PRINCIPAL_PROPERTY), any(Value.class));
  }

  @Test
  public void checkForExistingDynamicAuthorizable() throws RepositoryException {
    when(userManager.getAuthorizable(FOREIGN_PRINCIPAL_ID)).thenReturn(foreignGroup);
    foreignPrincipalManager.activate(new HashMap<String, String>());
    verify(userManager, never()).createGroup(any(Principal.class));
  }
}
