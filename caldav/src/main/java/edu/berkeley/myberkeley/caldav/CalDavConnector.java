package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.report.CalDavConstants;
import edu.berkeley.myberkeley.caldav.report.CalendarMultiGetReportInfo;
import edu.berkeley.myberkeley.caldav.report.CalendarQueryReportInfo;
import edu.berkeley.myberkeley.caldav.report.Filter;
import edu.berkeley.myberkeley.caldav.report.RequestCalendarData;
import edu.berkeley.myberkeley.caldav.report.TimeRange;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.property.Categories;
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
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.output.ByteArrayOutputStream;
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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;

public class CalDavConnector {

    public static final Categories MYBERKELEY_REQUIRED = new Categories("MyBerkeley-Required");

    public static final Categories MYBERKELEY_ARCHIVED = new Categories("MyBerkeley-Archived");

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnector.class);

    private static final Set<Integer> ALLOWABLE_HTTP_STATUS_CODES = new HashSet<Integer>(Arrays.asList(
            HttpStatus.SC_OK, HttpStatus.SC_CREATED, HttpStatus.SC_NO_CONTENT, HttpStatus.SC_MULTI_STATUS));

    private final HttpClient client = new HttpClient();

    private final URI serverRoot;

    private final URI userHome;

    private final String username;

    public CalDavConnector(String username, String password, URI serverRoot, URI userHome) {
        HttpState httpState = new HttpState();
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        httpState.setCredentials(AuthScope.ANY, credentials);
        this.client.setState(httpState);
        this.serverRoot = serverRoot;
        this.userHome = userHome;
        this.username = username;
    }

    /**
     * Returns the user's calendar entries (all of them) as a set of URIs
     * by doing a PROPFIND on a user calendar home, eg:
     * http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/
     */
    public List<CalendarUri> getCalendarUris() throws CalDavException {
        List<CalendarUri> uris = new ArrayList<CalendarUri>();
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
                            CalendarUri calUri = new CalendarUri(
                                    new URI(this.serverRoot, response.getHref(), false),
                                    etag.getValue().toString());
                            uris.add(calUri);
                        } catch (ParseException pe) {
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
     *
     * @return The URI of the newly created calendar entry.
     */
    public URI putCalendar(Calendar calendar, String ownerID) throws CalDavException {
        return modifyCalendar(null, calendar, ownerID);
    }

    /**
     * Write the specified calendar at the specified URI, deleting the previous entry if one already exists at the URI.
     *
     * @return The URI of the calendar entry.
     */
    public URI modifyCalendar(URI uri, Calendar calendar, String ownerID) throws CalDavException {
        if (uri == null) {
            try {
                uri = new URI(this.userHome, UUID.randomUUID() + ".ics", false);
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
            LOGGER.error("Got unsupported encoding exception", uee);
        }
        executeMethod(put);
        restrictPermissions(uri, ownerID);
        return uri;
    }

    /**
     * Deletes a calendar entry at specified uri.
     */
    public void deleteCalendar(URI uri) throws CalDavException {
        DeleteMethod deleteMethod = new DeleteMethod(uri.toString());
        executeMethod(deleteMethod);
    }

    /**
     * Get calendar entries by their URIs. Use the output of #getCalendarUris as the input to this method.
     */
    public List<CalendarWrapper> getCalendars(List<CalendarUri> uris) throws CalDavException {
        if (uris.isEmpty()) {
            return new ArrayList<CalendarWrapper>(0);
        }
        List<String> uriStrings = new ArrayList<String>(uris.size());
        for (CalendarUri uri : uris) {
            uriStrings.add(uri.toString());
        }
        ReportInfo reportInfo = new CalendarMultiGetReportInfo(new RequestCalendarData(), uriStrings);
        return search(reportInfo);
    }

    public List<CalendarWrapper> searchByDate(CalendarSearchCriteria criteria) throws CalDavException {
        Filter vcalComp = new Filter("VCALENDAR");
        Filter subcomponent = new Filter(criteria.getComponent().toString());
        subcomponent.setTimeRange(new TimeRange(criteria.getStart(), criteria.getEnd()));
        vcalComp.setCompFilter(Arrays.asList(subcomponent));

        ReportInfo reportInfo = new CalendarQueryReportInfo(new RequestCalendarData(), vcalComp);
        List<CalendarWrapper> rawResults = search(reportInfo);
        return filterResults(rawResults, criteria);
    }

    private List<CalendarWrapper> search(ReportInfo reportInfo) throws CalDavException {
        ReportMethod report = null;
        List<CalendarWrapper> calendars = new ArrayList<CalendarWrapper>();

        try {
            String reportURL = this.userHome.toString();
            report = new ReportMethod(reportURL, reportInfo);

            ByteArrayOutputStream requestOut = new ByteArrayOutputStream();
            report.getRequestEntity().writeRequest(requestOut);
            LOGGER.info("Request body: " + requestOut.toString("utf-8"));

            this.client.executeMethod(report);

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
        LOGGER.debug("Response headers: ");
        for (Header header : request.getResponseHeaders()) {
            LOGGER.debug(header.getName() + ": " + header.getValue());
        }
    }

    private void restrictPermissions(URI uri, String ownerID) throws CalDavException {
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
            aclMethod = new AclMethod(uri.toString(), acl);
            executeMethod(aclMethod);
        } catch (IOException ioe) {
            LOGGER.error("Got exception setting ACL", ioe);
        }
    }

    // filter in memory for now because Bedework has bugs searching on categories.
    // TODO do the searching on the Bedework side if bugs get fixed.
    private List<CalendarWrapper> filterResults(List<CalendarWrapper> rawResults, CalendarSearchCriteria criteria) {
        List<CalendarWrapper> filteredResults = new ArrayList<CalendarWrapper>(rawResults.size());
        for (CalendarWrapper wrapper : rawResults) {
            Component component = wrapper.getCalendar().getComponent(criteria.getComponent().toString());
            switch (criteria.getMode()) {
                case REQUIRED:
                    if (isRequired(component) && !isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case UNREQUIRED:
                    if (!isRequired(component) && !isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case ALL_UNARCHIVED:
                    if (!isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case ALL_ARCHIVED:
                    if (isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
            }
        }
        return filteredResults;
    }

    private boolean isRequired(Component comp) {
        PropertyList propList = comp.getProperties(Property.CATEGORIES);
        return propList != null && propList.contains(MYBERKELEY_REQUIRED);
    }

    private boolean isArchived(Component comp) {
        PropertyList propList = comp.getProperties(Property.CATEGORIES);
        return propList != null && propList.contains(MYBERKELEY_ARCHIVED);
    }

}
