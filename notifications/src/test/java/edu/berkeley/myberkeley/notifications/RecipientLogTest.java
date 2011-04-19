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

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.berkeley.myberkeley.caldav.api.CalDavException;
import org.apache.sling.commons.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import java.io.IOException;

public class RecipientLogTest extends NotificationTests {

  @Mock
  private Session session;

  @Mock
  private ContentManager contentManager;

  @Mock
  private AccessControlManager accessControlManager;

  public RecipientLogTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() throws IOException, JSONException, CalDavException, AccessDeniedException, StorageClientException {
    when(this.session.getContentManager()).thenReturn(this.contentManager);
    when(this.session.getAccessControlManager()).thenReturn(this.accessControlManager);
    when(this.contentManager.exists("/notification/path/" + RecipientLog.STORE_NAME)).thenReturn(false);
    Content content = mock(Content.class);
    when(this.contentManager.get("/notification/path/" + RecipientLog.STORE_NAME)).thenReturn(content);
  }

  @Test
  public void newLog() throws IOException, JSONException, CalDavException, AccessDeniedException, StorageClientException {
    RecipientLog log = new RecipientLog("/notification/path", this.session);
    assertNotNull(log);
    assertNotNull(log.getRecipientToCalendarURIMap());
    assertNull(log.getEmailMessageID());
    verify(this.accessControlManager, times(1)).setAcl(anyString(), anyString(), Matchers.<AclModification[]>any());
  }

  @Test
  public void update() throws IOException, JSONException, CalDavException, AccessDeniedException, StorageClientException {
    RecipientLog log = new RecipientLog("/notification/path", this.session);
    verify(this.accessControlManager, times(1)).setAcl(anyString(), anyString(), Matchers.<AclModification[]>any());
    assertNull(log.getEmailMessageID());

    log.setEmailMessageID("foo");
    log.getRecipientToCalendarURIMap().put("key1", "val1");
    log.update(this.contentManager);
    assertNotNull(log.getRecipientToCalendarURIMap().get("key1"));
    assertEquals("foo", log.getEmailMessageID());
  }

}
