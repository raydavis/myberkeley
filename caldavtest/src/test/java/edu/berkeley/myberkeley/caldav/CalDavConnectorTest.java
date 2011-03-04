package edu.berkeley.myberkeley.caldav;

import junit.framework.Assert;
import org.junit.Test;

public class CalDavConnectorTest extends Assert {

    @Test
    public void verifyServer() {
        CalDavConnector connector = new CalDavConnector();
        assertNotNull(connector);
    }

}
