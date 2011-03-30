package edu.berkeley.myberkeley.notifications;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import java.io.IOException;
import javax.servlet.ServletException;

@SlingServlet(resourceTypes = {"myberkeley/notification"}, selectors = {"create"}, methods = {"POST"}, generateComponent = true, generateService = true)
@Properties(value = {
        @Property(name = "service.vendor", value = "MyBerkeley"),
        @Property(name = "service.description", value = "Endpoint to create a notification")})
public class CreateNotificationServlet extends SlingAllMethodsServlet {

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        return;
    }
}
