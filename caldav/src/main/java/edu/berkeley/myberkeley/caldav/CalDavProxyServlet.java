package edu.berkeley.myberkeley.caldav;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@ServiceDocumentation(
        name = "Calendar Signup Servlet",
        description = "This servlet handles signing up the current user to an event then copying that event to the user's calendar.",
        bindings = {
                @ServiceBinding(type = BindingType.TYPE,
                        bindings = {
                                "sakai/calendar-signup"
                        },
                        selectors = {
                                @ServiceSelector(name = "signup", description = "Binds to the signup selector.")
                        }
                )
        },
        methods = {
                @ServiceMethod(name = "POST", description = "Signup for a calendar event.",
                        response = {
                                @ServiceResponse(code = 200, description = "All processing finished successfully."),
                                @ServiceResponse(code = 400, description = "User is already signed up for the event."),
                                @ServiceResponse(code = 401, description = "POST by anonymous user."),
                                @ServiceResponse(code = 500, description = "Failed to copy event to user's calendar or any exceptions encountered during processing.")
                        }
                )
        }
)
@SlingServlet(resourceTypes = {"sakai/calendar-signup"}, selectors = {"signup"}, methods = {"POST"}, generateComponent = true, generateService = true)

public class CalDavProxyServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavProxyServlet.class);

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        // Keep out anon users.
        if (UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Anonymous users can't use the CalDAV Proxy Service.");
            return;
        }

    }
}

