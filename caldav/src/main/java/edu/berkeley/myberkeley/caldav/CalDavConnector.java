package edu.berkeley.myberkeley.caldav;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

    private void logRequest(HttpMethod request) {
        try {
            LOGGER.info("Request on uri " + request.getURI());
        } catch ( URIException uie ) {
            LOGGER.error("Got URIException when trying to log request", uie);
        }
        LOGGER.info("Status: " + request.getStatusCode() + " " + request.getStatusText());
        for (Header header : request.getResponseHeaders()) {
            LOGGER.info(header.getName() + ": " + header.getValue());
        }
    }
}
