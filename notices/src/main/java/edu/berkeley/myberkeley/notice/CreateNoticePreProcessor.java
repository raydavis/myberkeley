package edu.berkeley.myberkeley.notice;

import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.message.CreateMessagePreProcessor;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessagingException;

import edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants;

/**
 * Checks if the message the user wants to create has all the right properties
 * on it.
 */
@Component(immediate = true, label = "CreateNoticePreProcessor", description = "Checks request for Notices")
@Service
@Properties(value = { @Property(name = "service.vendor", value = "University of California, Berkeley"),
        @Property(name = "service.description", value = "Checks if the user is allowed to create a message of type notice."),
        @Property(name = "sakai.message.createpreprocessor", value = "notice") })
public class CreateNoticePreProcessor implements CreateMessagePreProcessor {

    public void checkRequest(SlingHttpServletRequest request) throws MessagingException {
        if (request.getRequestParameter(MessageConstants.PROP_SAKAI_TO) == null) {
            throw new MessagingException(HttpServletResponse.SC_BAD_REQUEST, "The " + MessageConstants.PROP_SAKAI_TO + " parameter has to be specified.");
        }
    }

    public String getType() {
        return MyBerkeleyMessageConstants.TYPE_NOTICE;
    }

}
