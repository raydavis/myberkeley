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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks each SparseMapContent row for a path with no associated content. These
 * are records which should have been removed from storage but were not. Currently
 * three types are known:
 * <ul>
 *   <li>Records marked for deletion with a "_deleted" property. These should not
 *   be delivered to any clients, including PropertyMigrator services, but may be
 *   because of SparseMapContent bugs.</li>
 *   <li>Structure records which were not marked for deletion when the corresponding
 *   content records were.</li>
 *   <li>Older versions of deleted versioned content which were mistakenly left
 *   untouched.</li>
 * </ul>
 * With "dryRun=true", this service collects stats. The results can then be checked
 * at "http://localhost:8080/system/myberkeley/missingContent.json".
 * With "dryRun=false", the service will mark dangling structure records and
 * orphaned version records for deletion. The "other" category will be left alone
 * for further analysis. (In initial tests, marking the bad "/~211159/private/path"
 * for deletion had the side-effect of deleting the good "a:211159/private/path".)
 * <p>
 * After an upgrade run, a summary of the results can be found at:
 *   http://localhost:8080/system/myberkeley/missingContent.json
 * </p>
 */
@SlingServlet(methods = { "GET" }, paths = {"/system/myberkeley/missingContent"},
    generateService = false, generateComponent = true)
@Service({Servlet.class, PropertyMigrator.class})
public class MissingContentMigrator extends SlingSafeMethodsServlet implements PropertyMigrator {
  private static final Logger LOGGER = LoggerFactory.getLogger(MissingContentMigrator.class);

  @Reference
  transient protected Repository repository;

  private Set<String> nullPathsWithDeleteY = Sets.newHashSet();
  private Set<String> nullPathsWithCid = Sets.newHashSet();
  private Set<String> nullPathsWithNewerVersion = Sets.newHashSet();
  private Set<String> nullPathsOther = Sets.newHashSet();

  private Collection knownPathsCache = new CircularFifoBuffer(10000);

  @Override
  public boolean migrate(String rowId, Map<String, Object> properties) {
    boolean handled = false;
    if (properties.containsKey("_path")) {
      String path = properties.get("_path").toString();
      if (!knownPathsCache.contains(path)) {
        Session session = null;
        try {
          session = repository.loginAdministrative();
          Content content = session.getContentManager().get(path);
          if (content == null) {
            LOGGER.warn("Null content for {}", properties);
            if (properties.containsKey("_:cid")) {
              nullPathsWithCid.add(path);
              markForDeletion(properties);
              handled = true;
            } else if (properties.get("_nextVersion") != null) {
              nullPathsWithNewerVersion.add(path);
              markForDeletion(properties);
              handled = true;
            } else if ("Y".equals(properties.get("_deleted"))) {
              nullPathsWithDeleteY.add(path);
            } else {
              nullPathsOther.add(path);
            }
          } else {
            knownPathsCache.add(path);
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
    }
    return handled;
  }

  private void markForDeletion(Map<String, Object> properties) {
    properties.put("_deleted", "Y");
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

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    try {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      JSONWriter write = new JSONWriter(response.getWriter());
      write.object();
      write.key("nullContentCount")
          .value(Sets.union(nullPathsOther, Sets.union(nullPathsWithCid, nullPathsWithDeleteY)).size());
      write.key("deletedContentWithStructure").array();
      for (String path : Sets.difference(nullPathsWithCid, nullPathsWithDeleteY)) {
        write.value(path);
      }
      write.endArray();
      write.key("deletedContentWithoutStructure").array();
      for (String path : Sets.difference(nullPathsWithDeleteY, nullPathsWithCid)) {
        write.value(path);
      }
      write.endArray();
      write.key("deletedContentWithNewerVersion").array();
      for (String path : nullPathsWithNewerVersion) {
        write.value(path);
      }
      write.endArray();
      write.key("oddities").array();
      for (String path : nullPathsOther) {
        write.value(path);
      }
      write.endArray();
      write.endObject();
    } catch (JSONException e) {
      LOGGER.warn(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create the proper JSON structure.");
    }

  }
}
