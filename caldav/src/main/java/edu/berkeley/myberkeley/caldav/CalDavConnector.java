package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.report.CalDavConstants;
import edu.berkeley.myberkeley.caldav.report.CalendarMultiGetReportInfo;
import edu.berkeley.myberkeley.caldav.report.RequestCalendarData;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.client.methods.AclMethod;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.client.methods.PutMethod;
import org.apache.jackrabbit.webdav.client.methods.ReportMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;

public class CalDavConnector {

    public static final String MYBERKELEY_REQUIRED_PROPERTY_NAME = Component.EXPERIMENTAL_PREFIX + "MYBERKELEY-REQUIRED";

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnector.class);

    private static final Set<Integer> ALLOWABLE_HTTP_STATUS_CODES = new HashSet<Integer>(Arrays.asList(
            HttpStatus.SC_OK, HttpStatus.SC_CREATED, HttpStatus.SC_NO_CONTENT, HttpStatus.SC_MULTI_STATUS));

    private final HttpClient client = new HttpClient();

    private final String uri;

    private final String username;

    public CalDavConnector(String username, String password, String uri) {
        HttpState httpState = new HttpState();
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        httpState.setCredentials(AuthScope.ANY, credentials);
        this.client.setState(httpState);
        this.uri = uri;
        this.username = username;
    }

    public void getOptions() throws CalDavException {
        executeMethod(new OptionsMethod(this.uri));
    }

    /**
     * Returns the user's calendar entries (all of them) as a set of HREFs
     * by doing a PROPFIND on a user calendar home, eg:
     * http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/
     */
    public List<String> getCalendarHrefs() throws CalDavException {
        List<String> hrefs = new ArrayList<String>();
        try {
            PropFindMethod propFind = executeMethod(new PropFindMethod(this.uri));
            MultiStatusResponse[] responses = propFind.getResponseBodyAsMultiStatus().getResponses();
            for (MultiStatusResponse response : responses) {
                if (response.getHref().endsWith(".ics")) {
                    Status[] status = response.getStatus();
                    if (status.length == 1 && status[0].getStatusCode() == HttpServletResponse.SC_OK) {
                        hrefs.add(response.getHref());
                    }
                }
            }
        } catch (IOException ioe) {
            throw new CalDavException("IO error getting calendar hrefs ", ioe);
        } catch (DavException de) {
            throw new CalDavException("DavException getting calendar hrefs", de);
        }
        return hrefs;
    }

    public void putCalendar(String uri, Calendar calendar, String ownerID) throws CalDavException {
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

    public void deleteCalendar(String uri) throws CalDavException {
        DeleteMethod deleteMethod = new DeleteMethod(uri);
        executeMethod(deleteMethod);
    }

    public List<Calendar> getCalendars(List<String> hrefs) throws CalDavException {
        ReportInfo reportInfo = new CalendarMultiGetReportInfo(new RequestCalendarData(), hrefs);
        ReportMethod report = null;
        List<Calendar> calendars = new ArrayList<Calendar>();
        if (hrefs.isEmpty()) {
            return calendars;
        }
        try {
            report = new ReportMethod(this.uri, reportInfo);
            this.client.executeMethod(report);

            MultiStatus multiStatus = report.getResponseBodyAsMultiStatus();
            for (MultiStatusResponse msResponse : multiStatus.getResponses()) {
                DavPropertySet propSet = msResponse.getProperties(HttpServletResponse.SC_OK);
                DavProperty prop = propSet.get(
                        CalDavConstants.CALDAV_XML_CALENDAR_DATA, CalDavConstants.CALDAV_NAMESPACE);
                CalendarBuilder builder = new CalendarBuilder();
                net.fortuna.ical4j.model.Calendar c = builder.build(
                        new StringReader(prop.getValue().toString()));
                calendars.add(c);
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
        for (Header header : request.getResponseHeaders()) {
            LOGGER.info(header.getName() + ": " + header.getValue());
        }
    }
}
