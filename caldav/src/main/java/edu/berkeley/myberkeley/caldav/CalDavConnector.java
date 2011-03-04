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
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
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

public class CalDavConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnector.class);

    private final HttpClient client = new HttpClient();

    public CalDavConnector(String username, String password) {
        HttpState httpState = new HttpState();
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        httpState.setCredentials(AuthScope.ANY, credentials);
        this.client.setState(httpState);
    }

    public void getOptions(String uri) throws IOException {
        OptionsMethod options = null;
        try {
            options = new OptionsMethod(uri);
            this.client.executeMethod(options);
            logRequest(options);
        } catch (HttpClientError hce) {
            LOGGER.error("Error getting OPTIONS on uri " + uri, hce);
        } finally {
            if (options != null) {
                options.releaseConnection();
            }
        }
    }

    public void putCalendar(String uri, Calendar calendar) throws IOException {
        PutMethod put = null;
        try {
            put = new PutMethod(uri);
            put.addRequestHeader("If-None-Match", "*");
            put.setRequestEntity(new StringRequestEntity(calendar.toString(), "text/calendar", "UTF-8"));
            this.client.executeMethod(put);
            logRequest(put);
        } catch (HttpClientError hce) {
            LOGGER.error("Error doing PUT on uri " + uri, hce);
        } finally {
            if (put != null) {
                put.releaseConnection();
            }
        }
    }

    public void doReport(String uri, ReportInfo reportInfo)
            throws IOException, DavException, ParserException {
        ReportMethod report = null;

        try {
            report = new ReportMethod(uri, reportInfo);
            this.client.executeMethod(report);

            MultiStatus multiStatus = report.getResponseBodyAsMultiStatus();
            for (int i = 0; i < multiStatus.getResponses().length; i++) {
                MultiStatusResponse multiRes = multiStatus.getResponses()[i];
                String href = multiRes.getHref();
                DavPropertySet propSet = multiRes.getProperties(HttpServletResponse.SC_OK);
                DavProperty prop = propSet.get(
                        CalDavConstants.CALDAV_XML_CALENDAR_DATA, CalDavConstants.CALDAV_NAMESPACE);
                System.err.println("HREF: " + href);
                CalendarBuilder builder = new CalendarBuilder();

                net.fortuna.ical4j.model.Calendar c = builder.build(
                        new StringReader(prop.getValue().toString()));
                System.err.println("calendar-data: " + c.toString());
            }
        } finally {
            if (report != null) {
                report.releaseConnection();
            }
        }
    }

    private void logRequest(HttpMethod request) {
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
