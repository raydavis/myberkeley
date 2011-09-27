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

package edu.berkeley.myberkeley.notifications;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.parameters.ContainerRequestParameter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

public class CreateNotificationServletTest extends NotificationTests {

  private CreateNotificationServlet servlet;

  @Mock
  private SlingHttpServletRequest request;

  @Mock
  private SlingHttpServletResponse response;

  public CreateNotificationServletTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() {
    this.servlet = new CreateNotificationServlet();
  }

  @Test
  public void badParam() throws ServletException, IOException {
    this.servlet.doPost(this.request, this.response);
    verify(this.response).sendError(Mockito.eq(HttpServletResponse.SC_BAD_REQUEST),
            Mockito.anyString());
  }

  @Test
  public void doPost() throws ServletException, IOException, StorageClientException, AccessDeniedException, JSONException {
    String json = readCalendarNotificationFromFile();
    when(this.request.getRequestParameter(CreateNotificationServlet.POST_PARAMS.notification.toString())).thenReturn(
            new ContainerRequestParameter(json, "utf-8"));

    // sparse store
    Session session = mock(Session.class);
    ContentManager contentManager = mock(ContentManager.class);
    when(session.getContentManager()).thenReturn(contentManager);
    ResourceResolver resolver = mock(ResourceResolver.class);
    when(this.request.getResourceResolver()).thenReturn(resolver);

    // user's home dir
    Resource resource = mock(Resource.class);
    when(this.request.getResource()).thenReturn(resource);
    when(resource.adaptTo(Content.class)).thenReturn(new Content("/_user/home", new HashMap<String, Object>()));

    javax.jcr.Session jcrSession = mock(javax.jcr.Session.class, Mockito.withSettings().extraInterfaces(SessionAdaptable.class));
    when(((SessionAdaptable) jcrSession).getSession()).thenReturn(session);
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(jcrSession);

    AccessControlManager accessControlManager = mock(AccessControlManager.class);
    when(session.getAccessControlManager()).thenReturn(accessControlManager);

    String storePath = StorageClientUtils.newPath("/_user/home", Notification.STORE_NAME);
    when(contentManager.get(storePath)).thenReturn(
            new Content(storePath, new HashMap<String, Object>()));

    String id = "b6455aa7-1cf4-4839-8a90-62dc352648f4";
    String notificationPath = StorageClientUtils.newPath(storePath, id);
    when(contentManager.get(notificationPath)).thenReturn(
            new Content(notificationPath, new HashMap<String, Object>()));

    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(printWriter);

    this.servlet.doPost(this.request, this.response);
    printWriter.flush();

    JSONObject responseBody = new JSONObject(stringWriter.toString());
    assertNotNull(responseBody);
    assertEquals(id, responseBody.getString("id"));
  }
}
