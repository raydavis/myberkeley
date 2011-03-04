package edu.berkeley.myberkeley.caldav;

import junit.framework.Assert;
import org.junit.Test;

import java.io.IOException;

public class CalDavConnectorTest extends Assert {

    @Test
    public void getOptions() throws IOException {
        CalDavConnector connector = new CalDavConnector();
        assertNotNull(connector);
        connector.getOptions();
    }

}
