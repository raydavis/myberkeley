package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.report.CalDavConstants;
import edu.berkeley.myberkeley.caldav.report.CalendarMultiGetReportInfo;
import edu.berkeley.myberkeley.caldav.report.RequestCalendarData;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.client.methods.*;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.security.AclProperty;
import org.apache.jackrabbit.webdav.security.Principal;
import org.apache.jackrabbit.webdav.security.Privilege;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.*;

public class CalDavConnector {

    public static final String MYBERKELEY_REQUIRED = "MyBerkeley-Required";

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnector.class);

    private static final Set<Integer> ALLOWABLE_HTTP_STATUS_CODES = new HashSet<Integer>(Arrays.asList(
            HttpStatus.SC_OK, HttpStatus.SC_CREATED, HttpStatus.SC_NO_CONTENT, HttpStatus.SC_MULTI_STATUS));

    private final HttpClient client = new HttpClient();

    private final String baseUri;

    private final String username;

    public CalDavConnector(String username, String password, String baseUri) {
        HttpState httpState = new HttpState();
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        httpState.setCredentials(AuthScope.ANY, credentials);
        this.client.setState(httpState);
        this.baseUri = baseUri;
        this.username = username;
    }

    /**
     * Returns the URI of a calendar, given its UID.
     */
    public String buildUri(String calendarUID) {
        return this.baseUri + calendarUID + ".ics";
    }

    /**
     * Returns the user's calendar entries (all of them) as a set of URIs
     * by doing a PROPFIND on a user calendar home, eg:
     * http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/
     */
    public List<CalendarUri> getCalendarUris() throws CalDavException {
        List<CalendarUri> uris = new ArrayList<CalendarUri>();
        try {
            PropFindMethod propFind = executeMethod(new PropFindMethod(this.baseUri));
            MultiStatusResponse[] responses = propFind.getResponseBodyAsMultiStatus().getResponses();
            for (MultiStatusResponse response : responses) {
                if (response.getHref().endsWith(".ics")) {
                    Status[] status = response.getStatus();
                    if (status.length == 1 && status[0].getStatusCode() == HttpServletResponse.SC_OK) {
                        DavPropertySet propSet = response.getProperties(HttpServletResponse.SC_OK);
                        DavProperty etag = propSet.get(DavPropertyName.GETETAG);
                        try {
                            CalendarUri calUri = new CalendarUri(response.getHref(), etag.getValue().toString());
                            uris.add(calUri);
                        } catch ( ParseException pe ) {
                            throw new CalDavException("Invalid etag date", pe);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            throw new CalDavException("IO error getting calendar URIs ", ioe);
        } catch (DavException de) {
            throw new CalDavException("DavException getting calendar URIs", de);
        }
        return uris;
    }

    /**
     * Write the specified calendar and grant appropriate permissions on it to ownerID.
     * @return The URI of the newly created calendar entry.
     */
    public String putCalendar(Calendar calendar, String ownerID) throws CalDavException {
        return modifyCalendar(null, calendar, ownerID);
    }

    /**
     * Write the specified calendar at the specified URI, deleting the previous entry if one already exists at the URI.
     * @return The URI of the calendar entry.
     */
    public String modifyCalendar(String uri, Calendar calendar, String ownerID) throws CalDavException {
        if ( uri == null ) {
           uri = buildUri(UUID.randomUUID().toString());
        } else {
            deleteCalendar(uri);
        }
        PutMethod put = new PutMethod(uri);
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Saving calendar data: " + calendar.toString());
            }
            put.setRequestEntity(new StringRequestEntity(calendar.toString(), "text/calendar", "UTF-8"));
        } catch (UnsupportedEncodingException uee) {
            LOGGER.error("Got unsupported encoding exception", uee);
        }
        executeMethod(put);
        restrictPermissions(uri, ownerID);
        return uri;
    }

    /**
     * Deletes a calendar entry at specified uri.
     */
    public void deleteCalendar(String uri) throws CalDavException {
        DeleteMethod deleteMethod = new DeleteMethod(uri);
        executeMethod(deleteMethod);
    }

    /**
     * Get calendar entries by their URIs. Use the output of #getCalendarUris as the input to this method.
     */
    public List<CalendarWrapper> getCalendars(List<String> uris) throws CalDavException {
        ReportInfo reportInfo = new CalendarMultiGetReportInfo(new RequestCalendarData(), uris);
        ReportMethod report = null;
        List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();
        if (uris.isEmpty()) {
            return calendars;
        }
        try {
            report = new ReportMethod(this.baseUri, reportInfo);
            this.client.executeMethod(report);

            MultiStatus multiStatus = report.getResponseBodyAsMultiStatus();
            for (MultiStatusResponse response : multiStatus.getResponses()) {
                DavPropertySet propSet = response.getProperties(HttpServletResponse.SC_OK);
                DavProperty prop = propSet.get(
                        CalDavConstants.CALDAV_XML_CALENDAR_DATA, CalDavConstants.CALDAV_NAMESPACE);
                CalendarBuilder builder = new CalendarBuilder();
                net.fortuna.ical4j.model.Calendar calendar = builder.build(
                        new StringReader(prop.getValue().toString()));
                calendars.add(new CalendarWrapper(calendar, response.getHref()));
            }
        } catch (Exception e) {
            throw new CalDavException("Got exception doing report", e);
        } finally {
            if (report != null) {
                report.releaseConnection();
            }
        }
        return calendars;
    }

    private <T extends DavMethod> T executeMethod(T method) throws CalDavException {
        try {
            this.client.executeMethod(method);
            logRequest(method);
            checkStatus(method);
        } catch (HttpClientError hce) {
            throw new CalDavException("Error running " + method.getName(), hce);
        } catch (IOException ioe) {
            throw new CalDavException("IO error running " + method.getName(), ioe);
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
        return method;
    }

    private void checkStatus(DavMethod request) throws BadRequestException, URIException {
        if (!ALLOWABLE_HTTP_STATUS_CODES.contains(request.getStatusCode())) {
            LOGGER.error(request.getClass().getSimpleName() + " resulted in a bad request on uri " + request.getURI() + "; statusLine=" +
                    request.getStatusLine().toString());
            throw new BadRequestException("Bad request on uri " + request.getURI() + "; statusLine=" +
                    request.getStatusLine().toString());
        }
    }

    private void logRequest(DavMethod request) {
        try {
            LOGGER.info(request.getClass().getSimpleName() + " on uri " + request.getURI());
        } catch (URIException uie) {
            LOGGER.error("Got URIException when trying to log request", uie);
        }
        LOGGER.info("Status: " + request.getStatusLine().toString());
        LOGGER.info("Response headers: ");
        for (Header header : request.getResponseHeaders()) {
            LOGGER.info(header.getName() + ": " + header.getValue());
        }
    }

    private void restrictPermissions(String uri, String ownerID) throws CalDavException {
        // owner can only read and write, admin can do anything
        Principal owner = Principal.getHrefPrincipal("/principals/users/" + ownerID);
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
            aclMethod = new AclMethod(uri, acl);
            executeMethod(aclMethod);
        } catch (IOException ioe) {
            LOGGER.error("Got exception setting ACL", ioe);
        }
    }

}
