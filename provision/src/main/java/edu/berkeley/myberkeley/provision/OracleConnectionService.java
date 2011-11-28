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
package edu.berkeley.myberkeley.provision;

import edu.berkeley.myberkeley.api.provision.JdbcConnectionService;
import oracle.jdbc.pool.OracleDataSource;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Dictionary;

@Component(metatype = true, policy = ConfigurationPolicy.REQUIRE,
    label = "CalCentral :: Oracle Connection Service", description = "Connect to an Oracle database")
@Service
public class OracleConnectionService implements JdbcConnectionService {
  private static final Logger LOGGER = LoggerFactory.getLogger(OracleConnectionService.class);
  @Property(label = "Data Source URL",
      description = "OracleDataSource address in the form 'jdbc:oracle:thin:USER/PASSWORD@HOST:PORT:SID'")
  public static final String DATASOURCE_URL = "datasource.url";
  private String dataSourceUrl;
  private OracleDataSource dataSource;

  /**
   * @throws SQLException
   * @see edu.berkeley.myberkeley.api.provision.JdbcConnectionService#getConnection()
   */
  @Override
  public Connection getConnection() throws SQLException {
    Connection connection = dataSource.getConnection();
    return connection;
  }

  @Activate @Modified
  protected void activate(ComponentContext componentContext) {
    Dictionary<?, ?> props = componentContext.getProperties();
    LOGGER.info("New props = {}", props);
    dataSourceUrl = PropertiesUtil.toString(props.get(DATASOURCE_URL), null);
    Connection testConnection = null;
    try {
      dataSource = new OracleDataSource();
      dataSource.setURL(dataSourceUrl);
      testConnection = getConnection();
      DatabaseMetaData meta = testConnection.getMetaData();
      LOGGER.info("JDBC driver version is " + meta.getDriverVersion());
    } catch (SQLException e) {
      LOGGER.error(e.getMessage(), e);
      deactivate(componentContext);
      throw new ComponentException("Could not get test connection");
    } finally {
      if (testConnection != null) {
        try {
          testConnection.close();
        } catch (SQLException e) {
          LOGGER.error("Failed on final connection close", e);
        }
      }
    }
  }

  @SuppressWarnings("deprecation")
  @Deactivate
  protected void deactivate(ComponentContext componentContext) {
    if (dataSource != null) {
      try {
        dataSource.close();
      } catch (SQLException e) {
        LOGGER.info("Exception while closing data source", e);
      }
      dataSource = null;
    }
  }

}
