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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.util.Map;

/**
 * Provides properties to process the search
 */
@Component(immediate = true, label = "NotificationSearchPropertyProvider", description = "Provides notification search properties.")
@Service
@Properties(value = {
        @Property(name = "service.vendor", value = "The Sakai Foundation"),
        @Property(name = "service.description", value = "Provides notification search properties."),
        @Property(name = "sakai.search.provider", value = "Notification")})
public class NotificationSearchPropertyProvider implements SolrSearchPropertyProvider {

  public void loadUserProperties(SlingHttpServletRequest request,
                                 Map<String, String> propertiesMap) {
    String user = request.getRemoteUser();
    propertiesMap.put(Notification.SEARCH_PROP_NOTIFICATIONSTORE, ClientUtils
            .escapeQueryChars(LitePersonalUtils.PATH_AUTHORIZABLE + user + "/" + Notification.STORE_NAME));
  }

}
