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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import edu.berkeley.myberkeley.caldav.CalendarWrapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.SessionAdaptable;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.Writer;

public class NotificationSearchResultProcessorTest extends NotificationTests {

  private NotificationSearchResultProcessor processor;

  @Before
  public void setup() {
    this.processor = new NotificationSearchResultProcessor();
    processor.searchServiceFactory = mock(SolrSearchServiceFactory.class);
  }

  @Test
  public void testWriteResults() throws Exception {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    Session session = mock(Session.class);

    AccessControlManager accessControlManager = mock(AccessControlManager.class);
    when(session.getAccessControlManager()).thenReturn(accessControlManager);

    AuthorizableManager authMgr = mock(AuthorizableManager.class);
    when(session.getAuthorizableManager()).thenReturn(authMgr);

    ResourceResolver resolver = mock(ResourceResolver.class);
    when(request.getResourceResolver()).thenReturn(resolver);
    Object hybridSession = mock(javax.jcr.Session.class,
            withSettings().extraInterfaces(SessionAdaptable.class));
    when(resolver.adaptTo(javax.jcr.Session.class)).thenReturn(
            (javax.jcr.Session) hybridSession);
    when(((SessionAdaptable) hybridSession).getSession()).thenReturn(session);

    ContentManager cm = mock(ContentManager.class);
    when(session.getContentManager()).thenReturn(cm);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer w = new PrintWriter(baos);
    ExtendedJSONWriter writer = new ExtendedJSONWriter(w);

    Content contentA = new Content("/note/a", null);
    Notification notificationA = new Notification(new JSONObject(readNotificationFromFile()));
    notificationA.toContent("/note", contentA);

    when(cm.get("/note/a")).thenReturn(contentA);

    processor.writeResult(request, writer, mockResult(contentA));
    w.flush();

    String s = baos.toString("UTF-8");

    // make sure some key parts of the notification made it into json
    JSONObject json = new JSONObject(s);
    assertEquals("reminder", json.getString("category"));
    CalendarWrapper wrapper = CalendarWrapper.fromJSON(json.getJSONObject("calendarWrapper"));
    assertNotNull(wrapper);
    assertTrue(json.getJSONObject("uxState").getBoolean("validated"));
  }

  private Result mockResult(Content content) {
    Result r = mock(Result.class);
    when(r.getPath()).thenReturn(content.getPath());
    return r;
  }

}
