package edu.berkeley.myberkeley.notifications;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(selectors = {"myb-notificationstore"}, methods = {"POST"}, resourceTypes = {"sakai/user-home"},
        generateService = true, generateComponent = true)
@Properties(value = {
        @Property(name = "service.vendor", value = "MyBerkeley"),
        @Property(name = "service.description", value = "Endpoint to create a notification")})
public class CreateNotificationServlet extends SlingAllMethodsServlet {

    public enum POST_PARAMS {
        notification
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateNotificationServlet.class);

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        RequestParameter notificationParam = request.getRequestParameter(POST_PARAMS.notification.toString());
        if (notificationParam == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "The " + POST_PARAMS.notification + " parameter must not be null");
            return;
        }

        try {
            JSONObject notification = new JSONObject(notificationParam.toString());
            LOGGER.info("Notification = " + notification.toString(2));

        } catch (JSONException je) {
            LOGGER.error("Failed to convert notification to JSON", je);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "GOt a JSONException parsing input");
        }

        Resource r = request.getResource();
        Content home = r.adaptTo(Content.class);
        LOGGER.info("Home = {}", home);
    }
}
