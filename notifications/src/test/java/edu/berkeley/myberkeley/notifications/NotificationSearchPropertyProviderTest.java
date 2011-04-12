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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.util.HashMap;
import java.util.Map;

public class NotificationSearchPropertyProviderTest extends NotificationTests {

  private NotificationSearchPropertyProvider provider;

  @Before
  public void setup() {
    this.provider = new NotificationSearchPropertyProvider();
  }

  @Test
  public void testLoadUserProperties() throws Exception {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getRemoteUser()).thenReturn("joe");
    Map<String, String> props = new HashMap<String, String>();

    provider.loadUserProperties(request, props);
    assertEquals(ClientUtils.escapeQueryChars(LitePersonalUtils.PATH_AUTHORIZABLE + "joe/" +
            Notification.STORE_NAME), props.get(Notification.SEARCH_PROP_NOTIFICATIONSTORE));

  }
}
