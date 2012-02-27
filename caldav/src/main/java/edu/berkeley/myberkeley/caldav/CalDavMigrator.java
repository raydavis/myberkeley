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

import com.google.common.collect.ImmutableList;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavConnectorProvider;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@SlingServlet(methods = { "POST" }, paths = {"/system/myberkeley/calDavMigrator"},
    generateService = false, generateComponent = true)
@Service({Servlet.class, CalDavMigrator.class})
public class CalDavMigrator extends SlingAllMethodsServlet {
  private static final Logger LOGGER = LoggerFactory.getLogger(CalDavMigrator.class);
  public enum REQUEST_PARAMS {
    userIds,
    calDavServer,
    calDavPassword
  }
  public static final String ALL_USER_IDS_PARAM_VALUE = "ALL";

  @Reference
  CalDavConnectorProvider toCalDavProvider;
  @Reference
  DynamicListService dynamicListService;

  /**
   * @return number of records migrated
   */
  public long migrateCalDav(String owner, CalDavConnectorProvider fromCalDavProvider) throws IOException, CalDavException {
    long count = 0;
    CalDavConnector fromCalDav = fromCalDavProvider.getAdminConnector(owner);
    CalDavConnector toCalDav = toCalDavProvider.getAdminConnector(owner);
    List<CalendarURI> fromCalendarUris = fromCalDav.getCalendarUris();
    LOGGER.info("Owner {} has {} calendar records", owner, fromCalendarUris.size());
    List<CalendarWrapper> fromWrappers = fromCalDav.getCalendars(fromCalendarUris);
    for (CalendarWrapper fromWrapper : fromWrappers) {
      try {
        // Normalize the imported calendar format.
        LOGGER.debug(" from calendar {} : {}", count, fromWrapper);
        JSONObject fromJson = fromWrapper.toJSON();
        CalendarWrapper toWrapper = new CalendarWrapper(fromJson);
        LOGGER.debug("  to calendar : {}", toWrapper);
        toCalDav.putCalendar(toWrapper.getCalendar());
        count++;
      } catch (JSONException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    return count;
  }

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    if (!"admin".equals(request.getRemoteUser())) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    String[] userIdsParam = request.getParameterValues(REQUEST_PARAMS.userIds.toString());
    String calDavServer = request.getParameter(REQUEST_PARAMS.calDavServer.toString());
    String calDavPassword = request.getParameter(REQUEST_PARAMS.calDavPassword.toString());
    if (userIdsParam == null || calDavServer == null || calDavPassword == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The " +
          REQUEST_PARAMS.userIds.toString() + ", " +
          REQUEST_PARAMS.calDavServer.toString() + ", and " +
          REQUEST_PARAMS.calDavPassword.toString() + " parameters are required");
      return;
    }
    CalDavConnectorProviderImpl calDavConnectorProvider = new CalDavConnectorProviderImpl();
    calDavConnectorProvider.adminUsername = "admin";
    calDavConnectorProvider.adminPassword = calDavPassword;
    calDavConnectorProvider.calDavServerRoot = calDavServer;
    final Iterable<String> userIds;
    long totalMigrationCount = 0;
    try {
      Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
          javax.jcr.Session.class));
      if ((userIdsParam.length == 1) && (ALL_USER_IDS_PARAM_VALUE.equals(userIdsParam[0]))) {
        userIds = dynamicListService.getAllUserIds(session);
      } else {
        userIds = ImmutableList.copyOf(userIdsParam);
      }
      for (String userId : userIds) {
        final long migrationCount = migrateCalDav(userId, calDavConnectorProvider);
        writeToResponse("User " + userId + " migrated " + migrationCount + " tasks and events", response);
        totalMigrationCount += migrationCount;
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (CalDavException e) {
      LOGGER.error(e.getMessage(), e);
    }
    writeToResponse("Migrated a total of " + totalMigrationCount + " tasks and events", response);
  }

  static void writeToResponse(String msg, SlingHttpServletResponse response) {
    try {
      response.getWriter().write(msg + "\n");
      response.getWriter().flush(); // so the client sees updates
    } catch (IOException ioe) {
      LOGGER.error("Got IOException trying to write http response", ioe);
    }
  }

}
