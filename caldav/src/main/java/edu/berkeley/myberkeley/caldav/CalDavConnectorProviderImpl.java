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
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;

@Component(label = "MyBerkeley :: CalDavConnectorProvider",
        description = "Provider for CalDav server authentication information",
        immediate = false, metatype = true, policy = ConfigurationPolicy.REQUIRE)
@Service(value = CalDavConnectorProviderImpl.class)
public class CalDavConnectorProviderImpl implements CalDavConnectorProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnectorProviderImpl.class);

  @org.apache.felix.scr.annotations.Property(value = "admin", label = "CalDav Admin Username")
  protected static final String PROP_ADMIN_USERNAME = "caldavconnectorprovider.adminusername";

  @org.apache.felix.scr.annotations.Property(label = "CalDav Admin Password")
  protected static final String PROP_ADMIN_PASSWORD = "caldavconnectorprovider.adminpassword";

  @org.apache.felix.scr.annotations.Property(label = "CalDav Server Root")
  protected static final String PROP_SERVER_ROOT = "caldavconnectorprovider.serverroot";

  String adminUsername;

  String adminPassword;

  String calDavServerRoot;

@SuppressWarnings({"UnusedDeclaration"})
@Activate
@Modified
protected void activate(ComponentContext componentContext) throws Exception {
    Dictionary<?, ?> props = componentContext.getProperties();
    this.adminUsername = PropertiesUtil.toString(props.get(PROP_ADMIN_USERNAME), "admin");
    this.adminPassword = PropertiesUtil.toString(props.get(PROP_ADMIN_PASSWORD), "bedework");
    this.calDavServerRoot = StringUtils.stripToNull(PropertiesUtil.toString(props.get(PROP_SERVER_ROOT), null));
    LOGGER.info("calDavServerRoot = {}", this.calDavServerRoot);
    if (this.calDavServerRoot == null) {
      throw new ComponentException("Will not activate without " + PROP_SERVER_ROOT + " configuration");
    }
  }

  public CalDavConnector getAdminConnector(String owner) throws IOException {
    return new CalDavConnectorImpl(this.adminUsername, this.adminPassword,
            new URI(this.calDavServerRoot, false),
            new URI(this.calDavServerRoot + "/ucaldav/user/" + owner + "/calendar/", false), owner);
  }

  public CalDavConnector getConnector(String username) throws IOException {
    return new CalDavConnectorImpl(username, username,
            new URI(this.calDavServerRoot, false),
            new URI(this.calDavServerRoot + "/ucaldav/user/" + username + "/calendar/", false), username);
  }
}
