package edu.berkeley.myberkeley.caldav;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.jackrabbit.webdav.client.methods.OptionsMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CalDavConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavConnector.class);

    public void getOptions() throws IOException {
        HttpClient client = new HttpClient();

        HttpState httpState = new HttpState();
        Credentials credentials = new UsernamePasswordCredentials("vbede", "bedework");
        AuthScope scope = new AuthScope("test.media.berkeley.edu", 8080);
        httpState.setCredentials(scope, credentials);
        client.setState(httpState);

        String uri = "http://test.media.berkeley.edu:8080/ucaldav/";
        OptionsMethod options = null;

        try {
            options = new OptionsMethod(uri);
            client.executeMethod(options);
            LOGGER.info("OPTIONS method ran on uri " + uri);
            for ( Header header : options.getResponseHeaders() ) {
                LOGGER.info(header.getName() + ": " + header.getValue());
            }
        } catch ( HttpClientError hce ) {
            LOGGER.error("Error getting OPTIONS on uri " + uri, hce);
        } finally {
            if ( options != null ) {
                options.releaseConnection();
            }
        }
    }
}
