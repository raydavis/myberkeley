package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.report.CalDavConstants;
import edu.berkeley.myberkeley.caldav.report.CalendarMultiGetReportInfo;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.client.methods.PutMethod;
import org.apache.jackrabbit.webdav.client.methods.ReportMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class CalDavConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnector.class);

    private final HttpClient client = new HttpClient();

    public CalDavConnector(String username, String password) {
        HttpState httpState = new HttpState();
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        httpState.setCredentials(AuthScope.ANY, credentials);
        this.client.setState(httpState);
    }

    public void getOptions(String uri) throws CalDavException {
        executeMethod(new OptionsMethod(uri));
    }

    /**
     * Returns the user's calendar entries (all of them) as a set of HREFs
     * by doing a PROPFIND on a user calendar home, eg:
     * http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/
     */
    public List<String> getCalendarHrefs(String uri) throws CalDavException {
        List<String> hrefs = new ArrayList<String>();
        try {
            PropFindMethod propFind = executeMethod(new PropFindMethod(uri));
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

    public void putCalendar(String uri, Calendar calendar) throws CalDavException {
        PutMethod put = new PutMethod(uri);
        put.addRequestHeader("If-None-Match", "*");
        try {
            put.setRequestEntity(new StringRequestEntity(calendar.toString(), "text/calendar", "UTF-8"));
        } catch (UnsupportedEncodingException uee) {
            LOGGER.error("Got unsupported encoding exception", uee);
        }
        executeMethod(put);
    }

    public List<Calendar> doReport(String uri, ReportInfo reportInfo) throws CalDavException {
        ReportMethod report = null;
        List<Calendar> calendars = new ArrayList<Calendar>();
        try {
            report = new ReportMethod(uri, reportInfo);
            this.client.executeMethod(report);

            MultiStatus multiStatus = report.getResponseBodyAsMultiStatus();
            for (MultiStatusResponse msResponse : multiStatus.getResponses() ) {
                DavPropertySet propSet = msResponse.getProperties(HttpServletResponse.SC_OK);
                DavProperty prop = propSet.get(
                        CalDavConstants.CALDAV_XML_CALENDAR_DATA, CalDavConstants.CALDAV_NAMESPACE);
                CalendarBuilder builder = new CalendarBuilder();
                net.fortuna.ical4j.model.Calendar c = builder.build(
                        new StringReader(prop.getValue().toString()));
                calendars.add(c);
            }
        } catch ( Exception e ) {
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

    private void logRequest(DavMethod request) {
        try {
            LOGGER.info("Request on uri " + request.getURI());
        } catch (URIException uie) {
            LOGGER.error("Got URIException when trying to log request", uie);
        }
        LOGGER.info("Status: " + request.getStatusCode() + " " + request.getStatusText());
        for (Header header : request.getResponseHeaders()) {
            LOGGER.info(header.getName() + ": " + header.getValue());
        }
    }
}
