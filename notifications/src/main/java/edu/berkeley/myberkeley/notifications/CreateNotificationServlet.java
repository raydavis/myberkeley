package edu.berkeley.myberkeley.notifications;

import com.google.common.collect.ImmutableMap;
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
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(selectors = {"myb-notificationstore"}, methods = {"POST"}, resourceTypes = {"sakai/user-home"},
        generateService = true, generateComponent = true)
@Properties(value = {
        @Property(name = "service.vendor", value = "MyBerkeley"),
        @Property(name = "service.description", value = "Endpoint to create a notification")})
public class CreateNotificationServlet extends SlingAllMethodsServlet {

    public static final String NOTIFICATION_STORE_NAME = "_myberkeley_notificationstore";

    public static final String NOTIFICATION_STORE_RESOURCETYPE = "myberkeley/notificationstore";

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

        Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
                javax.jcr.Session.class));

        try {
            ContentManager contentManager = session.getContentManager();
            String storePath = StorageClientUtils.newPath(home.getPath(), NOTIFICATION_STORE_NAME);
            if (!contentManager.exists(storePath)) {
                LOGGER.info("Will create a new notification store for user at path " + storePath);
                contentManager.update(new Content(storePath, ImmutableMap.of(
                        JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                        (Object) NOTIFICATION_STORE_RESOURCETYPE)));
                List<AclModification> modifications = new ArrayList<AclModification>();
                AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
                AccessControlManager accessControlManager = session.getAccessControlManager();
                accessControlManager.setAcl(Security.ZONE_CONTENT, storePath, modifications.toArray(new AclModification[modifications.size()]));
            }

            Content content = contentManager.get(storePath);
            LOGGER.info("Content = {}", content);

        } catch (StorageClientException e) {
            throw new ServletException(e.getMessage(), e);
        } catch (AccessDeniedException ade) {
            throw new ServletException(ade.getMessage(), ade);
        }

    }
}
