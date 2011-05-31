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

package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavConnectorProvider;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;

import java.util.Dictionary;

@Component(label = "MyBerkeley :: CalDavConnectorProvider",
        description = "Provider for CalDav server authentication information",
        immediate = true, metatype = true)
@Service(value = CalDavConnectorProvider.class)
public class CalDavConnectorProviderImpl implements CalDavConnectorProvider {

  @org.apache.felix.scr.annotations.Property(value = "admin", label = "CalDav Admin Username")
  protected static final String PROP_ADMIN_USERNAME = "caldavconnectorprovider.adminusername";

  @org.apache.felix.scr.annotations.Property(value = "bedework", label = "CalDav Admin Password")
  protected static final String PROP_ADMIN_PASSWORD = "caldavconnectorprovider.adminpassword";

  @org.apache.felix.scr.annotations.Property(value = "http://test.media.berkeley.edu:8080", label = "CalDav Server Root")
  protected static final String PROP_SERVER_ROOT = "caldavconnectorprovider.serverroot";

  String adminUsername;

  String adminPassword;

  String calDavServerRoot;

  @SuppressWarnings({"UnusedDeclaration"})
  protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    this.adminUsername = PropertiesUtil.toString(props.get(PROP_ADMIN_USERNAME), "admin");
    this.adminPassword = PropertiesUtil.toString(props.get(PROP_ADMIN_PASSWORD), "bedework");
    this.calDavServerRoot = PropertiesUtil.toString(props.get(PROP_SERVER_ROOT), "http://test.media.berkeley.edu:8080");

  }

  public CalDavConnector getAdminConnector(String owner) throws URIException {
    return new CalDavConnectorImpl(this.adminUsername, this.adminPassword,
            new URI(this.calDavServerRoot, false),
            new URI(this.calDavServerRoot + "/ucaldav/user/" + owner + "/calendar/", false), owner);
  }

  public CalDavConnector getConnector(String username, String password) throws URIException {
    return new CalDavConnectorImpl(username, password,
            new URI(this.calDavServerRoot, false),
            new URI(this.calDavServerRoot + "/ucaldav/user/" + username + "/calendar/", false), username);
  }
}
