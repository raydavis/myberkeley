package edu.berkeley.myberkeley.notice;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Services;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageProfileWriter;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.api.message.MessageTransport;
import org.sakaiproject.nakamura.api.message.MessagingService;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants;

/**
 * Handler for notices. Needs to be started immediately to make sure it
 * registers with JCR as soon as possible.
 */

/**
 * Handler for notice messages.
 */
@Component(label = "NoticeHandler", description = "Handler for internally delivered notices.", immediate = true)
@Services(value = { @Service(value = MessageTransport.class), @Service(value = MessageProfileWriter.class) })
@Properties(value = { @Property(name = "service.vendor", value = "University of California, Berkeley"),
        @Property(name = "service.description", value = "Handler for internally delivered notice messages") })
public class NoticeHandler implements MessageTransport, MessageProfileWriter {
    private static final Logger LOG = LoggerFactory.getLogger(NoticeHandler.class);

    @Reference
    protected transient SlingRepository slingRepository;

    @Reference
    protected transient MessagingService messagingService;

    /**
     * {@inheritDoc}
     * 
     * @see org.sakaiproject.nakamura.api.message.MessageTransport#send(org.sakaiproject.nakamura.api.message.MessageRoutes,
     *      org.osgi.service.event.Event, javax.jcr.Node)
     */
    public void send(MessageRoutes routes, Event event, Node originalNotice) {
        try {
            Session session = slingRepository.loginAdministrative(null);

            for (MessageRoute route : routes) {
                if (MyBerkeleyMessageConstants.NOTICE_TRANSPORT.equals(route.getTransport())) {
                    LOG.info("Started a notice routing.");
                    String rcpt = route.getRcpt();
                    // the path were we want to save messages in.
                    String messageId = originalNotice.getProperty(MessageConstants.PROP_SAKAI_ID).getString();
                    String toPath = messagingService.getFullPathToMessage(rcpt, messageId, session);

                    // Copy the node into the user his folder.
                    JcrUtils.deepGetOrCreateNode(session, toPath.substring(0, toPath.lastIndexOf("/")));
                    session.save();
                    session.getWorkspace().copy(originalNotice.getPath(), toPath);
                    Node n = JcrUtils.deepGetOrCreateNode(session, toPath);

                    // Add some extra properties on the just created node.
                    n.setProperty(MessageConstants.PROP_SAKAI_READ, false);
                    n.setProperty(MessageConstants.PROP_SAKAI_MESSAGEBOX, MessageConstants.BOX_INBOX);
                    n.setProperty(MessageConstants.PROP_SAKAI_SENDSTATE, MessageConstants.STATE_NOTIFIED);
                    // only put the single recipient into this property, not all of them
                    n.setProperty(MessageConstants.PROP_SAKAI_TO, route.getRcpt());

                    if (session.hasPendingChanges()) {
                        session.save();
                    }
                }
            }
        }
        catch (RepositoryException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public void writeProfileInformation(Session session, String recipient, JSONWriter write) {

    }

    protected void bindMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    protected void unbindMessagingService(MessagingService messagingService) {
        this.messagingService = null;
    }

    protected void bindSlingRepository(SlingRepository slingRepository) {
        this.slingRepository = slingRepository;
    }

    protected void unbindSlingRepository(SlingRepository slingRepository) {
        this.slingRepository = null;
    }

    public String getType() {
        return MyBerkeleyMessageConstants.TYPE_NOTICE;
    }
}
