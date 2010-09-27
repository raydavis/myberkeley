package edu.berkeley.myberkeley.notice;

import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_CATEGORY;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_DUEDATE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_EVENTDATE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_TASKSTATE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.SAKAI_CATEGORY_REMINDER;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.TYPE_NOTICE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_FROM;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_MESSAGEBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_SENDSTATE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_SUBJECT;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_TO;

import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.sakaiproject.nakamura.api.message.CreateMessagePreProcessor;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessagingException;

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
        StringBuilder errorBuilder = null;
        MessagingException mex = null;
        if (request.getRequestParameter(PROP_SAKAI_TO) == null) {
            if (errorBuilder == null) errorBuilder = new StringBuilder();
            if (mex == null) mex = new MessagingException();
            errorBuilder = errorBuilder.append(PROP_SAKAI_TO + " request parameter is missing. ");
        }
        if (request.getRequestParameter(PROP_SAKAI_FROM) == null) {
            if (errorBuilder == null) errorBuilder = new StringBuilder();
            if (mex == null) mex = new MessagingException();
            errorBuilder = errorBuilder.append( PROP_SAKAI_FROM + " request parameter is mising. ");
        }
        if (request.getRequestParameter(PROP_SAKAI_SUBJECT) == null) {
            if (errorBuilder == null) errorBuilder = new StringBuilder();
            if (mex == null) mex = new MessagingException();
            errorBuilder = errorBuilder.append(PROP_SAKAI_SUBJECT + " request parameter is missing. ");
        }   
        if (request.getRequestParameter(PROP_SAKAI_SENDSTATE) == null) {
            if (errorBuilder == null) errorBuilder = new StringBuilder();
            if (mex == null) mex = new MessagingException();
            errorBuilder = errorBuilder.append(PROP_SAKAI_SENDSTATE + " request parameter is missing. ");
        }   
        if (request.getRequestParameter(PROP_SAKAI_MESSAGEBOX) == null) {
            if (errorBuilder == null) errorBuilder = new StringBuilder();
            if (mex == null) mex = new MessagingException();
            errorBuilder = errorBuilder.append(PROP_SAKAI_MESSAGEBOX + " request parameter is missing. ");
        }  
        if (request.getRequestParameter(PROP_SAKAI_CATEGORY) == null) {
            if (errorBuilder == null) errorBuilder = new StringBuilder();
            if (mex == null) mex = new MessagingException();
            errorBuilder = errorBuilder.append(PROP_SAKAI_CATEGORY + " request parameter is missing. ");
        }  
        if (request.getRequestParameter(MessageConstants.PROP_SAKAI_TYPE) == null) {
            if (errorBuilder == null) errorBuilder = new StringBuilder();
            if (mex == null) mex = new MessagingException();
            errorBuilder = errorBuilder.append(MessageConstants.PROP_SAKAI_TYPE + " request parameter is missing. ");
        }  
        else {
            RequestParameter messageCategoryParam = request.getRequestParameter(MessageConstants.PROP_SAKAI_TYPE);
            String messageCategory = messageCategoryParam.getString();
            if (SAKAI_CATEGORY_REMINDER.equalsIgnoreCase(messageCategory)) {
                if (request.getRequestParameter(PROP_SAKAI_TASKSTATE) == null) {
                    if (errorBuilder == null) errorBuilder = new StringBuilder();
                    if (mex == null) mex = new MessagingException();
                    errorBuilder = errorBuilder.append(PROP_SAKAI_TASKSTATE + " request parameter is missing. ");
                }         
                if (request.getRequestParameter(PROP_SAKAI_DUEDATE) == null && 
                        request.getRequestParameter(PROP_SAKAI_EVENTDATE) == null) {
                    if (errorBuilder == null) errorBuilder = new StringBuilder();
                    if (mex == null) mex = new MessagingException();
                    errorBuilder = errorBuilder.append("either a " + PROP_SAKAI_DUEDATE + " or a "
                            + PROP_SAKAI_EVENTDATE + " request parameter is missing.");  
                }
            }
        }
//        2010-07-15T17:41:47.785-07:00
        if (mex != null) {
            throw new MessagingException(HttpServletResponse.SC_BAD_REQUEST, errorBuilder.toString());
        }
    }

    public String getType() {
        return TYPE_NOTICE;
    }

}
