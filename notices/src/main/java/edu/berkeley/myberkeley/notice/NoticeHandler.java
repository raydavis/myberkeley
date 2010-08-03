package edu.berkeley.myberkeley.notice;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Services;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageProfileWriter;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.api.message.MessageTransport;
import org.sakaiproject.nakamura.api.message.MessagingService;
import org.sakaiproject.nakamura.api.presence.PresenceService;
import org.sakaiproject.nakamura.api.presence.PresenceUtils;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
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

    @Reference
    protected transient MessageProfileWriter messageProfileWriter;
    

    @Property(value = { "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss",
            "dd.MM.yyyy" })
    private static final String PROP_DATE_FORMAT = "notice.dateFormats";

    private List<DateFormat> dateFormats = new LinkedList<DateFormat>();

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
                    // put the group recipient expansion here
                    String rcpt = route.getRcpt();
                    // the path were we want to save messages in.
                    String messageId = originalNotice.getProperty(MessageConstants.PROP_SAKAI_ID).getString();
                    String toPath = messagingService.getFullPathToMessage(rcpt, messageId, session);

                    // Copy the node into the recipient's folder.
                    JcrUtils.deepGetOrCreateNode(session, toPath.substring(0, toPath.lastIndexOf("/")));
                    session.save();
                    session.getWorkspace().copy(originalNotice.getPath(), toPath);
                    Node n = JcrUtils.deepGetOrCreateNode(session, toPath);
                    LOG.debug("created recipient mesaage node: " + n.toString());
                    // need to due this conversion issue on new recipient node
                    handleDueDate(originalNotice, n);
                    // Add some extra properties on the just created node.
                    n.setProperty(MessageConstants.PROP_SAKAI_READ, false);
                    n.setProperty(MessageConstants.PROP_SAKAI_MESSAGEBOX, MessageConstants.BOX_INBOX);
                    n.setProperty(MessageConstants.PROP_SAKAI_SENDSTATE, MessageConstants.STATE_NOTIFIED);
                    // only put the single recipient into this property, not all
                    // of them
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

    /**
     * method to make sure the recipient notice node stores a Date object
     * sometimes the originalNotice has a String and sometimes a Date at this
     * point, assuming that's a concurrency issue with the SlingPostServlet's
     * date parsing running before or after this method. Haven't tracked it down
     * exactly but this logic fixes the issue making sure the recipient notice
     * has a Date dueDate
     * 
     * @param originalNotice
     * @param newNotice
     * @throws PathNotFoundException
     * @throws RepositoryException
     */
    private void handleDueDate(Node originalNotice, Node newNotice) throws PathNotFoundException, RepositoryException {
        javax.jcr.Property dueDateProp = originalNotice.getProperty(MyBerkeleyMessageConstants.PROP_SAKAI_DUEDATE);
        if (dueDateProp != null) {
            LOG.debug("handleDueDate() got source dueDateProp: " + dueDateProp);
            Value value = dueDateProp.getValue();
            int dueDateValueType = value.getType();
            LOG.debug("handleDueDate() got source dueDateProp Value Type: " + dueDateValueType);
            Calendar senderDueDate = null;
            if (PropertyType.STRING == dueDateValueType) {
                senderDueDate = parse(value.getString());
            }
            else if (PropertyType.DATE == dueDateValueType) {
                senderDueDate = dueDateProp.getDate();
            }
            else {
                LOG.error("handleDueDate() sourceDueDate is not a String or Date, is PropertyType: " + dueDateValueType);
            }
            if (senderDueDate != null) {
                LOG.debug("setting " + MyBerkeleyMessageConstants.PROP_SAKAI_DUEDATE + " to " + senderDueDate.getTime().toString() + " in newNotice: "
                        + newNotice.toString());
                newNotice.setProperty(MyBerkeleyMessageConstants.PROP_SAKAI_DUEDATE, senderDueDate);
            }
            else {
                LOG.error("handleDueDate() sourceDueDate is null");
            }
        }
        else {
            LOG.info("handleDueDate() no dueDate in sender's originalNotice, perhaps it's not a Reminder?");
        }
    }

    /**
     * copied from SlingPostServlet DateParser helper Parses the given source
     * string and returns the respective calendar instance. If no format matches
     * returns <code>null</code>.
     * <p/>
     * Note: method is synchronized because SimpleDateFormat is not.
     * 
     * @param source
     *            date time source string
     * @return calendar representation of the source or <code>null</code>
     */
    public synchronized Calendar parse(String source) {
        for (DateFormat fmt : this.dateFormats) {
            try {
                Date d = fmt.parse(source);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Parsed " + source + " using " + ((SimpleDateFormat) fmt).toPattern() + " into " + d);
                }
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                return c;
            }
            catch (ParseException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed parsing " + source + " using " + ((SimpleDateFormat) fmt).toPattern());
                }
            }
        }
        return null;
    }

    public String getType() {
        return MyBerkeleyMessageConstants.TYPE_NOTICE;
    }

    /**
     * delegate to the InternalMessageHandler for now
     * {@inheritDoc}
     * 
     * @see org.sakaiproject.nakamura.api.message.MessageProfileWriter#writeProfileInformation(javax.jcr.Session,
     *      java.lang.String, org.apache.sling.commons.json.io.JSONWriter)
     */
    public void writeProfileInformation(Session session, String recipient, JSONWriter write) {
        this.messageProfileWriter.writeProfileInformation(session, recipient, write);
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
    
    protected void bindMessageProfileWriter(MessageProfileWriter messageProfileWriter) {
        this.messageProfileWriter = messageProfileWriter;
    }
    
    protected void unbindMessageProfileWriter(MessageProfileWriter messageProfileWriter) {
        this.messageProfileWriter = null;
    }

    protected void activate(ComponentContext context) {
        Dictionary<?, ?> props = context.getProperties();
        DateFormat dateFormat = null;
        String[] dateFormatStrs = OsgiUtil.toStringArray(props.get(PROP_DATE_FORMAT));
        for (String dateFormatStr : dateFormatStrs) {
            dateFormat = new SimpleDateFormat(dateFormatStr, Locale.US);
            this.dateFormats.add(dateFormat);
        }
    }

    protected void deactivate(ComponentContext context) {
        dateFormats = null;
    }
}
