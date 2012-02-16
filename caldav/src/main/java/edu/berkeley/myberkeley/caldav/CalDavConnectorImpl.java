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

import edu.berkeley.myberkeley.caldav.api.BadRequestException;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import edu.berkeley.myberkeley.caldav.report.CalDavConstants;
import edu.berkeley.myberkeley.caldav.report.CalendarMultiGetReportInfo;
import edu.berkeley.myberkeley.caldav.report.CalendarQueryReportInfo;
import edu.berkeley.myberkeley.caldav.report.Filter;
import edu.berkeley.myberkeley.caldav.report.RequestCalendarData;
import edu.berkeley.myberkeley.caldav.report.TimeRange;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang.time.DateUtils;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.client.methods.AclMethod;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.client.methods.PutMethod;
import org.apache.jackrabbit.webdav.client.methods.ReportMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.security.AclProperty;
import org.apache.jackrabbit.webdav.security.Principal;
import org.apache.jackrabbit.webdav.security.Privilege;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletResponse;

public class CalDavConnectorImpl implements CalDavConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnectorImpl.class);

  private static final Set<Integer> ALLOWABLE_HTTP_STATUS_CODES = new HashSet<Integer>(Arrays.asList(
          HttpStatus.SC_OK, HttpStatus.SC_CREATED, HttpStatus.SC_NO_CONTENT, HttpStatus.SC_MULTI_STATUS));

  private final HttpClient client = new HttpClient();

  private final URI serverRoot;

  private final URI userHome;

  private final String username;

  private final String owner;

  public CalDavConnectorImpl(String username, String password, URI serverRoot, URI userHome, String owner) {
    HttpState httpState = new HttpState();
    Credentials credentials = new UsernamePasswordCredentials(username, password);
    httpState.setCredentials(AuthScope.ANY, credentials);
    this.client.setState(httpState);
    this.serverRoot = serverRoot;
    this.userHome = userHome;
    this.username = username;
    this.owner = owner;
  }

  /**
   * Returns the user's calendar entries (all of them) as a set of URIs
   * by doing a PROPFIND on a user calendar home, eg:
   * http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/
   */
  public List<CalendarURI> getCalendarUris() throws CalDavException, IOException {
    List<CalendarURI> uris = new ArrayList<CalendarURI>();
    try {
      PropFindMethod propFind = executeMethod(new PropFindMethod(this.userHome.toString()));
      MultiStatusResponse[] responses = propFind.getResponseBodyAsMultiStatus().getResponses();
      for (MultiStatusResponse response : responses) {
        if (response.getHref().endsWith(".ics")) {
          Status[] status = response.getStatus();
          if (status.length == 1 && status[0].getStatusCode() == HttpServletResponse.SC_OK) {
            DavPropertySet propSet = response.getProperties(HttpServletResponse.SC_OK);
            DavProperty etag = propSet.get(DavPropertyName.GETETAG);
            try {
              CalendarURI calUri = new CalendarURI(
                      new URI(this.serverRoot, response.getHref(), false),
                      etag.getValue().toString());
              uris.add(calUri);
            } catch (ParseException pe) {
              throw new CalDavException("Invalid etag date", pe);
            }
          }
        }
      }
    } catch (DavException de) {
      throw new CalDavException("DavException getting calendar URIs", de);
    }
    return uris;
  }

  /**
   * Write the specified calendar and grant appropriate permissions on it to ownerID.
   *
   * @return The URI of the newly created calendar entry.
   */
  public CalendarURI putCalendar(Calendar calendar) throws CalDavException, IOException {
    return modifyCalendar(null, calendar);
  }

  /**
   * Write the specified calendar at the specified URI, deleting the previous entry if one already exists at the URI.
   *
   * @return The URI of the calendar entry.
   */
  public CalendarURI modifyCalendar(CalendarURI uri, Calendar calendar) throws CalDavException, IOException {
    if (uri == null) {
      try {
        uri = new CalendarURI(
                new URI(this.userHome, UUID.randomUUID() + ".ics", false), new DateTime());
      } catch (URIException uie) {
        throw new CalDavException("Unexpected URIException", uie);
      }
    } else {
      deleteCalendar(uri);
    }
    PutMethod put = new PutMethod(uri.toString());
    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Saving calendar data: " + calendar.toString());
      }
      put.setRequestEntity(new StringRequestEntity(calendar.toString(), "text/calendar", "UTF-8"));
    } catch (UnsupportedEncodingException uee) {
      throw new CalDavException("Got unsupported encoding exception, does this server not like UTF-8?", uee);
    }
    executeMethod(put);
    restrictPermissions(uri);
    return uri;
  }

  /**
   * Deletes a calendar entry at specified uri.
   */
  public void deleteCalendar(CalendarURI uri) throws CalDavException, IOException {
    DeleteMethod deleteMethod = new DeleteMethod(uri.toString());
    executeMethod(deleteMethod);
  }

  /**
   * Get calendar entries by their URIs. Use the output of #getCalendarUris as the input to this method.
   */
  public List<CalendarWrapper> getCalendars(List<CalendarURI> uris) throws CalDavException, IOException {
    if (uris.isEmpty()) {
      return new ArrayList<CalendarWrapper>(0);
    }
    List<String> uriStrings = new ArrayList<String>(uris.size());
    for (CalendarURI uri : uris) {
      uriStrings.add(uri.toString());
    }
    ReportInfo reportInfo = new CalendarMultiGetReportInfo(new RequestCalendarData(), uriStrings);
    return search(reportInfo);
  }

  public List<CalendarWrapper> searchByDate(CalendarSearchCriteria criteria) throws CalDavException, IOException {
    Filter vcalComp = new Filter("VCALENDAR");
    Filter subcomponent = new Filter(criteria.getType().toString());
    subcomponent.setTimeRange(new TimeRange(criteria.getStart(), criteria.getEnd()));
    vcalComp.setCompFilter(Arrays.asList(subcomponent));

    ReportInfo reportInfo = new CalendarQueryReportInfo(new RequestCalendarData(), vcalComp);
    List<CalendarWrapper> rawResults = search(reportInfo);
    CalendarResultProcessor processor = new CalendarResultProcessor(rawResults, criteria);
    return processor.processResults();
  }

  public boolean hasOverdueTasks() throws CalDavException, IOException {
    ReportMethod report = null;

    Filter vcalComp = new Filter("VCALENDAR");
    Filter subcomponent = new Filter(Component.VTODO);

    Date midnightToday = new Date();
    midnightToday = DateUtils.setHours(midnightToday, 0);
    midnightToday = DateUtils.setMinutes(midnightToday, 0);
    midnightToday = DateUtils.setSeconds(midnightToday, 0);
    midnightToday = DateUtils.setMilliseconds(midnightToday, 0);
    DateFormat utcFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
    TimeZoneRegistry registry = new CalendarBuilder().getRegistry();
    TimeZone gmt = registry.getTimeZone("Europe/London");
    try {
      DateTime endTime = new DateTime(utcFormat.format(midnightToday), gmt);
      subcomponent.setTimeRange(new TimeRange(new DateTime(0), endTime));
      LOGGER.info("End time for overdue task search = " + endTime.toString());
    } catch (ParseException ignored) {
      // won't happen since we formatted the date ourselves
    }

    vcalComp.setCompFilter(Arrays.asList(subcomponent));
    ReportInfo reportInfo = new CalendarQueryReportInfo(new RequestCalendarData(), vcalComp);

    try {
      report = new ReportMethod(this.userHome.toString(), reportInfo);

      ByteArrayOutputStream requestOut = new ByteArrayOutputStream();
      report.getRequestEntity().writeRequest(requestOut);
      LOGGER.debug("Doing calendar search for overdue tasks; Request body: " + requestOut.toString("utf-8"));

      executeMethod(report);

      MultiStatus multiStatus = report.getResponseBodyAsMultiStatus();
      for (MultiStatusResponse response : multiStatus.getResponses()) {
        DavPropertySet propSet = response.getProperties(HttpServletResponse.SC_OK);
        DavProperty etag = propSet.get(DavPropertyName.GETETAG);
        if (etag != null) {
          DavProperty prop = propSet.get(
                  CalDavConstants.CALDAV_XML_CALENDAR_DATA, CalDavConstants.CALDAV_NAMESPACE);
          CalendarBuilder builder = new CalendarBuilder();
          net.fortuna.ical4j.model.Calendar calendar = builder.build(
                  new StringReader(prop.getValue().toString()));
          CalendarWrapper wrapper = new CalendarWrapper(
                  calendar,
                  new URI(this.serverRoot, response.getHref(), false),
                  etag.getValue().toString());
          if (!wrapper.isCompleted() && !wrapper.isArchived()) {
            return true;
          }
        }
      }

    } catch (DavException de) {
      throw new CalDavException("Got a webdav exception", de);
    } catch (ParserException pe) {
      throw new CalDavException("Invalid calendar data", pe);
    } finally {
      if (report != null) {
        report.releaseConnection();
      }
    }

    return false;
  }

  private List<CalendarWrapper> search(ReportInfo reportInfo) throws CalDavException, IOException {
    ReportMethod report = null;
    List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();

    try {
      String reportURL = this.userHome.toString();
      report = new ReportMethod(reportURL, reportInfo);

      ByteArrayOutputStream requestOut = new ByteArrayOutputStream();
      report.getRequestEntity().writeRequest(requestOut);
      LOGGER.debug("Doing calendar search; Request body: " + requestOut.toString("utf-8"));

      executeMethod(report);

      MultiStatus multiStatus = report.getResponseBodyAsMultiStatus();
      for (MultiStatusResponse response : multiStatus.getResponses()) {
        DavPropertySet propSet = response.getProperties(HttpServletResponse.SC_OK);
        DavProperty etag = propSet.get(DavPropertyName.GETETAG);
        DavProperty prop = propSet.get(
                CalDavConstants.CALDAV_XML_CALENDAR_DATA, CalDavConstants.CALDAV_NAMESPACE);
        if (prop != null) {
          CalendarBuilder builder = new CalendarBuilder();
          net.fortuna.ical4j.model.Calendar calendar = builder.build(
                  new StringReader(prop.getValue().toString()));
          calendars.add(new CalendarWrapper(
                  calendar,
                  new URI(this.serverRoot, response.getHref(), false),
                  etag.getValue().toString()));
        }
      }
    } catch (DavException de) {
      throw new CalDavException("Got a webdav exception", de);
    } catch (ParserException pe) {
      throw new CalDavException("Invalid calendar data", pe);
    } finally {
      if (report != null) {
        report.releaseConnection();
      }
    }
    return calendars;
  }

  private <T extends DavMethod> T executeMethod(T method) throws CalDavException, IOException {
    try {
      method.getParams().setSoTimeout(30000);
      this.client.executeMethod(method);
      logRequest(method);
      checkStatus(method);
    } catch (HttpClientError hce) {
      throw new CalDavException("Error running " + method.getName(), hce);
    } finally {
      if (method != null) {
        method.releaseConnection();
      }
    }
    return method;
  }

  private void checkStatus(DavMethod request) throws BadRequestException, URIException {
    if (!ALLOWABLE_HTTP_STATUS_CODES.contains(request.getStatusCode())) {
      LOGGER.debug(request.getClass().getSimpleName() + " resulted in a bad request on uri " + request.getURI() + "; statusLine=" +
              request.getStatusLine().toString());
      throw new BadRequestException("Bad request on uri " + request.getURI() + "; statusLine=" +
              request.getStatusLine().toString(), request.getStatusCode());
    }
  }

  private void logRequest(DavMethod request) {
    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(request.getClass().getSimpleName() + " on uri " + request.getURI());
      }
    } catch (URIException uie) {
      LOGGER.error("Got URIException when trying to log request", uie);
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Status: " + request.getStatusLine().toString());
      LOGGER.debug("Response headers: ");
      for (Header header : request.getResponseHeaders()) {
        LOGGER.debug(header.getName() + ": " + header.getValue());
      }
    }
  }

  private void restrictPermissions(CalendarURI uri) throws CalDavException {
    // owner can only read and write, admin can do anything
    Principal owner = Principal.getHrefPrincipal("/principals/users/" + this.owner);
    Principal admin = Principal.getHrefPrincipal("/principals/users/" + this.username);
    Privilege[] adminPrivs = new Privilege[]{Privilege.PRIVILEGE_ALL};
    Privilege[] ownerPrivs = new Privilege[]{Privilege.PRIVILEGE_READ, Privilege.PRIVILEGE_READ_ACL,
            Privilege.PRIVILEGE_WRITE_CONTENT, Privilege.PRIVILEGE_WRITE_PROPERTIES};
    AclProperty.Ace[] aces = new AclProperty.Ace[]{
            AclProperty.createDenyAce(owner, new Privilege[]{Privilege.PRIVILEGE_ALL}, false, false, null),
            AclProperty.createGrantAce(owner, ownerPrivs, false, false, null),
            AclProperty.createGrantAce(admin, adminPrivs, false, false, null)
    };
    AclProperty acl = new AclProperty(aces);
    AclMethod aclMethod;
    try {
      aclMethod = new AclMethod(uri.toString(), acl);
      executeMethod(aclMethod);
    } catch (IOException ioe) {
      throw new CalDavException("Got IO exception setting ACL", ioe);
    }
  }

  /**
   * All that's required to create an account is to log in once and
   * make any request.
   */
  @Override
  public void ensureCalendarStore() {
    GetMethod getMethod = new GetMethod(userHome.toString());
    try {
      getMethod.getParams().setSoTimeout(30000);
      this.client.executeMethod(getMethod);
    } catch (IOException e) {
      LOGGER.error("Error running " + getMethod.getName() + ": " + e.getMessage());
    } finally {
      getMethod.releaseConnection();
    }
  }

}
