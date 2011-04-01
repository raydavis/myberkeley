package edu.berkeley.myberkeley.notifications;

import com.google.common.collect.ImmutableMap;
import edu.berkeley.myberkeley.caldav.CalDavException;
import edu.berkeley.myberkeley.caldav.CalendarWrapper;
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
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(selectors = {"myb-notificationstore"}, methods = {"POST"}, resourceTypes = {"sakai/user-home"},
        generateService = true, generateComponent = true)
@Properties(value = {
        @Property(name = "service.vendor", value = "MyBerkeley"),
        @Property(name = "service.description", value = "Endpoint to create a notification")})
public class CreateNotificationServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = -1868784233373889299L;

    public static final String NOTIFICATION_RESOURCETYPE = "myberkeley/notification";

    public static final String NOTIFICATION_STORE_NAME = "_myberkeley_notificationstore";

    public static final String NOTIFICATION_STORE_RESOURCETYPE = "myberkeley/notificationstore";

    public enum POST_PARAMS {
        notification
    }

    public enum JSON_PROPERTIES {
        id,
        calendarWrapper
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateNotificationServlet.class);

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        RequestParameter notificationParam = request.getRequestParameter(POST_PARAMS.notification.toString());
        JSONObject notificationJSON;

        if (notificationParam == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "The " + POST_PARAMS.notification + " parameter must not be null");
            return;
        }

        try {
            notificationJSON = new JSONObject(notificationParam.toString());
            LOGGER.info("Notification = " + notificationJSON.toString(2));

        } catch (JSONException je) {
            LOGGER.error("Failed to convert notification to JSON", je);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "GOt a JSONException parsing input");
            return;
        }

        Resource r = request.getResource();
        Content home = r.adaptTo(Content.class);
        LOGGER.info("Home = {}", home);

        Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(
                javax.jcr.Session.class));

        try {
            ContentManager contentManager = session.getContentManager();
            String storePath = StorageClientUtils.newPath(home.getPath(), NOTIFICATION_STORE_NAME);
            Content store = createStoreIfNecessary(session, contentManager, storePath);
            LOGGER.info("Content = {}", store);

            String notificationPath = StorageClientUtils.newPath(storePath, getNotificationID(notificationJSON));
            Content notification = createNotificationIfNecessary(contentManager, notificationPath);
            setNotificationProperties(notificationJSON, notification);
            contentManager.update(notification);
            LOGGER.info("Saved a Notification;  data = {}", notification);

        } catch (StorageClientException e) {
            throw new ServletException(e.getMessage(), e);
        } catch (AccessDeniedException ade) {
            throw new ServletException(ade.getMessage(), ade);
        } catch (JSONException je) {
            throw new ServletException(je.getMessage(), je);
        } catch (CalDavException cde) {
            throw new ServletException(cde.getMessage(), cde);
        }
    }

    private void setNotificationProperties(JSONObject json, Content notification) throws JSONException, CalDavException {
        CalendarWrapper wrapper = CalendarWrapper.fromJSON(json.getJSONObject(JSON_PROPERTIES.calendarWrapper.toString()));
        notification.setProperty(JSON_PROPERTIES.calendarWrapper.toString(), wrapper);
    }

    private Content createNotificationIfNecessary(ContentManager contentManager, String notificationPath) throws AccessDeniedException, StorageClientException {
        if (!contentManager.exists(notificationPath)) {
            LOGGER.info("Creating new notification at path " + notificationPath);
            contentManager.update(new Content(notificationPath, ImmutableMap.of(
                    JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                    (Object) NOTIFICATION_RESOURCETYPE)));
        }
        return contentManager.get(notificationPath);
    }

    private Content createStoreIfNecessary(Session session, ContentManager contentManager, String storePath) throws AccessDeniedException, StorageClientException {
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
        return contentManager.get(storePath);
    }

    private String getNotificationID(JSONObject notificationJSON) {
        try {
            return notificationJSON.getString(JSON_PROPERTIES.id.toString());
        } catch (JSONException ignored) {
            // that's ok, we'll use the random UUID
            return UUID.randomUUID().toString();
        }
    }
}
