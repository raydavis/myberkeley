package edu.berkeley.myberkeley.caldav;

import junit.framework.Assert;
import org.junit.Test;

import java.io.IOException;

public class CalDavConnectorTest extends Assert {

    @Test
    public void getOptions() throws IOException {
        CalDavConnector connector = new CalDavConnector("vbede", "bedework");
        assertNotNull(connector);
        connector.getOptions("http://test.media.berkeley.edu:8080/ucaldav/");
    }

}
