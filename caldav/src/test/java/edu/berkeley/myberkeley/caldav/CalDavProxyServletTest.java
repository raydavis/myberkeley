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
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.Status;
import org.apache.commons.httpclient.URI;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.IOUtils;
import org.sakaiproject.nakamura.util.parameters.ContainerRequestParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

public class CalDavProxyServletTest extends CalDavTests {

  private CalDavProxyServlet servlet;

  private static final Logger LOGGER = LoggerFactory.getLogger(CalDavProxyServletTest.class);

  @Before
  public void setUp() throws Exception {
    Assume.assumeTrue(initializeCalDavSource());
    this.servlet = new CalDavProxyServlet();
    CalDavConnectorProviderImpl provider = new CalDavConnectorProviderImpl();
    provider.adminUsername = "admin";
    provider.adminPassword = calDavPassword;
    provider.calDavServerRoot = calDavServer;
    this.servlet.calDavConnectorProvider = provider;
  }

  @Test
  public void noAnonymousUsers() throws ServletException, IOException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
    when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);
    this.servlet.doGet(request, response);

    verify(response).sendError(Mockito.eq(HttpServletResponse.SC_UNAUTHORIZED),
            Mockito.anyString());
  }

  @Test
  public void adminUserGet() throws ServletException, IOException, JSONException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
    when(request.getRemoteUser()).thenReturn(UserConstants.ADMIN_USERID);
    when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.type.toString())).thenReturn(
            new ContainerRequestParameter(CalendarSearchCriteria.TYPE.VTODO.toString(), "utf-8"));
    ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
    when(response.getWriter()).thenReturn(new PrintWriter(responseStream));

    try {
      this.servlet.doGet(request, response);
      JSONObject json = new JSONObject(responseStream.toString("utf-8"));
      JSONArray results = (JSONArray) json.get("results");
      assertNotNull(results);
      Boolean hasOverdue = json.getBoolean("hasOverdueTasks");
      assertNotNull(hasOverdue);
    } catch (IOException ioe) {
      LOGGER.error("Trouble contacting bedework server", ioe);
    }
  }

  @Test
  public void handleGet() throws ServletException, IOException, CalDavException, ParseException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
    when(request.getRemoteUser()).thenReturn(UserConstants.ADMIN_USERID);
    when(response.getWriter()).thenReturn(new PrintWriter(System.out));
    CalDavConnector connector = mock(CalDavConnector.class);
    List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();
    calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/url1", false), RANDOM_ETAG));
    calendars.add(new CalendarWrapper(buildVevent("Test 2"), new URI("/url2", false), RANDOM_ETAG));
    calendars.add(new CalendarWrapper(buildVTodo("Todo Test 3"), new URI("/url3", false), RANDOM_ETAG));
    Calendar completed = buildVTodo("Completed todo");
    Component completedTodo = completed.getComponent(Component.VTODO);
    completedTodo.getProperties().add(Status.VTODO_COMPLETED);
    calendars.add(new CalendarWrapper(completed, new URI("/uri4", false), RANDOM_ETAG));

    Calendar archived = buildVTodo("Archived todo");
    Component archivedTodo = archived.getComponent(Component.VTODO);
    archivedTodo.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);
    calendars.add(new CalendarWrapper(archived, new URI("/uri5", false), RANDOM_ETAG));

    CalendarSearchCriteria criteria = new CalendarSearchCriteria();
    when(connector.searchByDate(criteria)).thenReturn(calendars);
    this.servlet.handleGet(response, connector, criteria);

  }

  @Test
  public void getCalendarSearchCriteria() throws ServletException, ParseException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.type.toString())).thenReturn(
            new ContainerRequestParameter("VTODO", "utf-8"));
    when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.mode.toString())).thenReturn(
            new ContainerRequestParameter(CalendarSearchCriteria.MODE.ALL_ARCHIVED.toString(), "utf-8"));
    when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.start_date.toString())).thenReturn(
            new ContainerRequestParameter(RANDOM_ETAG, "utf-8"));
    when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.end_date.toString())).thenReturn(
            new ContainerRequestParameter(MONTH_AFTER_RANDOM_ETAG, "utf-8"));

    CalendarSearchCriteria criteria = this.servlet.getCalendarSearchCriteria(request);
    assertEquals(CalendarSearchCriteria.TYPE.VTODO, criteria.getType());
    assertEquals(CalendarSearchCriteria.MODE.ALL_ARCHIVED, criteria.getMode());
    assertEquals(new DateTime(RANDOM_ETAG, "yyyyMMdd'T'HHmmss", true), criteria.getStart());
    assertEquals(new DateTime(MONTH_AFTER_RANDOM_ETAG, "yyyyMMdd'T'HHmmss", true), criteria.getEnd());
  }

  @Test
  public void getDefaultSearchCriteria() throws ServletException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    this.servlet.getCalendarSearchCriteria(request);
  }

  @Test(expected = ServletException.class)
  public void bogusStartDate() throws ServletException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.start_date.toString())).thenReturn(
            new ContainerRequestParameter("not a date", "utf-8"));

    this.servlet.getCalendarSearchCriteria(request);
  }

  @Test(expected = ServletException.class)
  public void bogusEndDate() throws ServletException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    when(request.getRequestParameter(CalDavProxyServlet.REQUEST_PARAMS.end_date.toString())).thenReturn(
            new ContainerRequestParameter("not a date either", "utf-8"));

    this.servlet.getCalendarSearchCriteria(request);
  }

  @Test
  public void noAnonymousUsersInPost() throws ServletException, IOException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
    when(request.getRemoteUser()).thenReturn(UserConstants.ANON_USERID);
    this.servlet.doPost(request, response);

    verify(response).sendError(Mockito.eq(HttpServletResponse.SC_UNAUTHORIZED),
            Mockito.anyString());
  }

  @Test
  public void updateCalendars() throws CalDavException, IOException, JSONException {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    InputStream in = getClass().getClassLoader().getResourceAsStream("postData.json");
    String json = IOUtils.readFully(in, "utf-8");
    when(request.getRequestParameter(CalDavProxyServlet.POST_PARAMS.calendars.toString())).thenReturn(
            new ContainerRequestParameter(json, "utf-8"));

    JSONArray batch = this.servlet.getCalendars(request);
    assertNotNull(batch);

    CalDavConnector connector = mock(CalDavConnector.class);
    List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();
    calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/cal1", false), RANDOM_ETAG));
    calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/cal2", false), RANDOM_ETAG));
    calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/cal3", false), RANDOM_ETAG));
    calendars.add(new CalendarWrapper(buildVevent("Test 1"), new URI("/cal4", false), RANDOM_ETAG));
    when(connector.getCalendars(Matchers.<List<CalendarURI>>any())).thenReturn(calendars);
    this.servlet.updateCalendars(request, connector);

  }
}
