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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * During the standard upgrade run, this module collects candidate paths for a document migration.
 * For 1.2.0, these are participant "public/pubspace" and "private/privspace" records.
 * The migration itself can then be triggered via:
 * <pre>
 *   curl -X POST -u admin:admin http://localhost:8080/system/myberkeley/fileMigrator
 * </pre>
 */
@SlingServlet(methods = { "POST" }, paths = {"/system/myberkeley/fileMigrator"},
    generateService = false, generateComponent = true)
@Service({Servlet.class, PropertyMigrator.class})
public class ForcedFileMigrator  extends SlingAllMethodsServlet implements PropertyMigrator {
  private static final Logger LOGGER = LoggerFactory.getLogger(ForcedFileMigrator.class);

  public static final String PATH_FIELD = "_path";
  public static final Pattern pathPattern = Pattern.compile("^a:\\S+/(public/pubspace|private/privspace).*$");

  @Reference
  transient protected FileMigrationService migrationService;

  private Set<String> candidatePathsToMigrate = Sets.newHashSet();

  @Override
  public boolean migrate(String rowId, Map<String, Object> properties) {
    // These checks are unDRYly copied from the 1.2.0 versions of the
    // org.sakaiproject.nakamura.files.migrator classes but wuddya gonna do?
    if (properties.containsKey(PATH_FIELD) && properties.containsKey(FileMigrationService.STRUCTURE_ZERO)) {
      String path = properties.get(PATH_FIELD).toString();
      if (pathPattern.matcher(path).matches()) {
        candidatePathsToMigrate.add(path);
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
    return ForcedFileMigrator.class.getName();
  }

  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, "false");
  }

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
        javax.jcr.Session.class));
    if (!isAdminUser(session)) {
      LOGGER.error(this.getClass().getSimpleName() + " called by " + session.getUserId());
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    long count = 0;
    try {
      ContentManager contentManager = session.getContentManager();
      for (String path : candidatePathsToMigrate) {
        Content content = contentManager.get(path);
        if (content != null) {
          if (migrationService.fileContentNeedsMigration(content)) {
            LOGGER.info("Need to migrate path: {}", path);
            migrationService.migrateFileContent(content);
            count++;
          }
        }
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
    try {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      JSONWriter write = new JSONWriter(response.getWriter());
      write.object();
      write.key("candidateCount").value(candidatePathsToMigrate.size());
      write.key("migratedCount").value(count);
      write.endObject();
    } catch (JSONException e) {
      LOGGER.warn(e.getMessage(), e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to create the proper JSON structure.");
    }
  }

  private static boolean isAdminUser(Session session) {
    try {
      User currentUser = (User) session.getAuthorizableManager().findAuthorizable(session.getUserId());
      return currentUser.isAdmin();
    } catch (AccessDeniedException e) {
      return false;
    } catch (StorageClientException e) {
      return false;
    }
  }

}
