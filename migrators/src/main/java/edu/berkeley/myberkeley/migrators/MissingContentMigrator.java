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
package edu.berkeley.myberkeley.migrators;

import static org.sakaiproject.nakamura.lite.content.InternalContent.PATH_FIELD;
import static org.sakaiproject.nakamura.lite.content.InternalContent.STRUCTURE_UUID_FIELD;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Component
public class MissingContentMigrator implements PropertyMigrator {
  private static final Logger LOGGER = LoggerFactory.getLogger(MissingContentMigrator.class);

  @Reference
  private Repository repository;

  @Override
  public boolean migrate(String rowId, Map<String, Object> properties) {
    if (properties.containsKey(PATH_FIELD) && !properties.containsKey(STRUCTURE_UUID_FIELD)) {
      String path = properties.get(PATH_FIELD).toString();
      Session session = null;
      try {
        session = repository.loginAdministrative();
        Content content = session.getContentManager().get(path);
        if (content == null) {
          LOGGER.warn("Null content for {}", path);
        }
      } catch (AccessDeniedException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (ClientPoolException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (StorageClientException e) {
        LOGGER.error(e.getMessage(), e);
      } finally {
        if (session != null) {
          try {
            session.logout();
          } catch (ClientPoolException e) {
            LOGGER.error("Unexpected exception logging out of session", e);
          }
        }
      }
    }
    return false;
  }

  @Override
  public String[] getDependencies() {
    return new String[0];
  }

  @Override
  public String getName() {
    return MissingContentMigrator.class.getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }
}
