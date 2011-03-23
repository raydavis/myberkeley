package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.Status;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@Service(value = Servlet.class)
@SlingServlet(paths = {"/system/myberkeley/caldav"}, methods = {"POST"}, generateComponent = true, generateService = true)

public class CalDavPostServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavGetServlet.class);

    public enum POST_PARAMS {
        uri,
        isArchived,
        isCompleted
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        // Keep out anon users.
        if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Anonymous users can't use the CalDAV Proxy Service.");
            return;
        }

        try {
            CalDavConnector connector = new CalDavConnector("admin", "bedework",
                    new URI("http://test.media.berkeley.edu:8080", false),
                    new URI("http://test.media.berkeley.edu:8080/ucaldav/user/vbede/calendar/", false));
            CalendarWrapper wrapper = getCalendarWrapper(request, connector);
            applyChangesToCalendar(request, connector, wrapper);
        } catch (Exception e) {
            LOGGER.error("Exception fetching calendar", e);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            response.getWriter().close();
        }

    }

    protected CalendarWrapper getCalendarWrapper(SlingHttpServletRequest request, CalDavConnector connector)
            throws CalDavException, IOException {
        RequestParameter uriParam = request.getRequestParameter(POST_PARAMS.uri.toString());
        CalendarURI uri = new CalendarURI(new URI(uriParam.toString(), false), new DateTime());
        List<CalendarWrapper> wrappers = connector.getCalendars(Arrays.asList(uri));
        return wrappers.get(0);
    }

    protected void applyChangesToCalendar(SlingHttpServletRequest request, CalDavConnector connector, CalendarWrapper wrapper)
            throws CalDavException, IOException {
        Component component = wrapper.getCalendar().getComponent(Component.VEVENT);
        if (component == null) {
            component = wrapper.getCalendar().getComponent(Component.VTODO);
        }

        component.getProperties().remove(CalDavConnector.MYBERKELEY_ARCHIVED);

        RequestParameter isArchived = request.getRequestParameter(POST_PARAMS.isArchived.toString());
        if (isArchived != null && isArchived.toString().equals("true")) {
            component.getProperties().add(CalDavConnector.MYBERKELEY_ARCHIVED);
        }
        RequestParameter isCompleted = request.getRequestParameter(POST_PARAMS.isCompleted.toString());
        if (isCompleted != null && isCompleted.toString().equals("true")) {
            component.getProperties().remove(Property.STATUS);
            component.getProperties().add(new Status(Status.COMPLETED));
        }

        // TODO set the correct owner of this calendar instead of hardcoding vbede
        connector.modifyCalendar(wrapper.getUri(), wrapper.getCalendar(), "vbede");
    }

}
