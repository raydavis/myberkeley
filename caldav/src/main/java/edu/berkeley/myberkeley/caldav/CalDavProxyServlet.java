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
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.Status;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service(value = Servlet.class)
@SlingServlet(paths = {"/system/myberkeley/caldav"}, methods = {"GET", "POST"}, generateComponent = true, generateService = true)

public class CalDavProxyServlet extends SlingAllMethodsServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(CalDavProxyServlet.class);

  private static final long serialVersionUID = 5522248522595237407L;

  @Reference
  CalDavConnectorProvider calDavConnectorProvider;

  public enum REQUEST_PARAMS {
    type,
    mode,
    start_date,
    end_date,
    sort
  }

  public enum POST_PARAMS {
    calendars
  }

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    // Keep out anon users.
    if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
              "Anonymous users can't use the CalDAV Proxy Service.");
      return;
    }

    try {
      CalDavConnector connector = this.calDavConnectorProvider.getAdminConnector(request.getRemoteUser());
      updateCalendars(request, connector);
    } catch (Exception e) {
      LOGGER.error("Exception fetching calendar", e);
      response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    } finally {
      response.getWriter().close();
    }

  }

  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
          throws ServletException, IOException {

    // Keep out anon users.
    if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
              "Anonymous users can't use the CalDAV Proxy Service.");
      return;
    }

    CalDavConnector connector = this.calDavConnectorProvider.getAdminConnector(request.getRemoteUser());

    CalendarSearchCriteria criteria = getCalendarSearchCriteria(request);

    try {
      handleGet(response, connector, criteria);
    } finally {
      response.getWriter().close();
    }
  }

  protected CalendarSearchCriteria getCalendarSearchCriteria(SlingHttpServletRequest request) throws ServletException {
    CalendarSearchCriteria criteria = new CalendarSearchCriteria();

    // apply non-default values from request if they're available

    RequestParameter type = request.getRequestParameter(REQUEST_PARAMS.type.toString());
    if (type != null) {
      criteria.setType(CalendarSearchCriteria.TYPE.valueOf(type.getString()));
    }
    RequestParameter mode = request.getRequestParameter(REQUEST_PARAMS.mode.toString());
    if (mode != null) {
      criteria.setMode(CalendarSearchCriteria.MODE.valueOf(mode.getString()));
    }
    RequestParameter startDate = request.getRequestParameter(REQUEST_PARAMS.start_date.toString());
    if (startDate != null) {
      try {
        criteria.setStart(new DateTime(startDate.getString()));
      } catch (ParseException pe) {
        throw new ServletException("Invalid start date passed: " + startDate.getString(), pe);
      }
    }
    RequestParameter endDate = request.getRequestParameter(REQUEST_PARAMS.end_date.toString());
    if (endDate != null) {
      try {
        criteria.setEnd(new DateTime(endDate.getString()));
      } catch (ParseException pe) {
        throw new ServletException("Invalid end date passed: " + endDate.getString(), pe);
      }
    }

    RequestParameter sort = request.getRequestParameter(REQUEST_PARAMS.sort.toString());
    if (sort != null) {
      criteria.setSort(CalendarSearchCriteria.SORT.valueOf(sort.getString()));
    }

    return criteria;
  }

  protected void handleGet(SlingHttpServletResponse response, CalDavConnector connector,
                           CalendarSearchCriteria criteria) throws IOException {
    List<CalendarWrapper> calendars;
    boolean hasOverdue = false;

    try {

      long begin = System.currentTimeMillis();
      calendars = connector.searchByDate(criteria);
      long end = System.currentTimeMillis();
      LOGGER.info("Got " + calendars.size() + " calendar records from Bedework in " + (end - begin) + "ms");

      if (criteria.getType().equals(CalendarSearchCriteria.TYPE.VTODO)) {
        begin = System.currentTimeMillis();
        hasOverdue = connector.hasOverdueTasks();
        end = System.currentTimeMillis();
        LOGGER.info("Got overdue-task result from Bedework in " + (end - begin) + "ms");
      }

    } catch (CalDavException e) {
      LOGGER.error("Exception fetching calendars", e);
      response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      JSONObject json = new JSONObject();
      JSONArray results = new JSONArray();
      for (CalendarWrapper wrapper : calendars) {
        results.put(wrapper.toJSON());
      }
      json.put("results", results);
      if (criteria.getType().equals(CalendarSearchCriteria.TYPE.VTODO)) {
        json.put("hasOverdueTasks", hasOverdue);
      }

      response.getWriter().write(json.toString(2));

    } catch (JSONException e) {
      LOGGER.error("Failed to convert calendar to JSON", e);
    }

  }

  protected JSONArray getCalendars(SlingHttpServletRequest request) throws JSONException {
    RequestParameter batchParam = request.getRequestParameter(POST_PARAMS.calendars.toString());
    if (batchParam != null) {
      return new JSONArray(batchParam.toString());
    }
    return null;
  }

  protected void updateCalendars(SlingHttpServletRequest request, CalDavConnector connector)
          throws JSONException, CalDavException, IOException {

    List<CalendarURI> uris = new ArrayList<CalendarURI>();
    JSONArray calendars = getCalendars(request);

    for (int i = 0; i < calendars.length(); i++) {
      String uriString = ((JSONObject) calendars.get(i)).getString("uri");
      CalendarURI uri = new CalendarURI(new URI(uriString, false), new DateTime());
      uris.add(uri);
    }

    List<CalendarWrapper> wrappers = connector.getCalendars(uris);
    Map<CalendarURI, CalendarWrapper> wrapperMap = new HashMap<CalendarURI, CalendarWrapper>();
    for (CalendarWrapper wrapper : wrappers) {
      wrapperMap.put(wrapper.getUri(), wrapper);
    }

    for (int i = 0; i < calendars.length(); i++) {
      JSONObject thisItem = (JSONObject) calendars.get(i);
      String uriString = thisItem.getString("uri");
      CalendarURI uri = new CalendarURI(new URI(uriString, false), new DateTime());
      CalendarWrapper wrapper = wrapperMap.get(uri);

      Component component = wrapper.getComponent();

      boolean isArchived = thisItem.getBoolean("isArchived");
      boolean isCompleted = thisItem.getBoolean("isCompleted");
      boolean isRead = thisItem.getBoolean("isRead");
      if (isArchived) {
        component.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);
      } else {
        component.getProperties().remove(CalDavConnector.MYBERKELEY_ARCHIVED);
      }
      if (isCompleted) {
        component.getProperties().remove(Status.VTODO_NEEDS_ACTION);
        component.getProperties().add(Status.VTODO_COMPLETED);
      } else {
        component.getProperties().remove(Status.VTODO_COMPLETED);
      }
      if (isRead) {
        component.getProperties().add(CalDavConnector.MYBERKELEY_READ);
      } else {
        component.getProperties().remove(CalDavConnector.MYBERKELEY_READ);
      }
      connector.modifyCalendar(wrapper.getUri(), wrapper.getCalendar());
    }
  }
}
