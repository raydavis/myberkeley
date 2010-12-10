package edu.berkeley.myberkeley.notice;

import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_DATA_NODE_NAME;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_PREFIX;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_QUERY;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_ROOT_NODE_NAME;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.NODE_PATH_PROPERTY;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.NOTICE_TRANSPORT;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_CATEGORY;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_DUEDATE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_EVENTDATE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.QUEUE_NAME;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.SAKAI_CATEGORY_REMINDER;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.TYPE_NOTICE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.BOX_INBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_FROM;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_ID;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_MESSAGEBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_READ;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_SENDSTATE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_TO;
import static org.sakaiproject.nakamura.api.message.MessageConstants.STATE_NOTIFIED;
import static org.sakaiproject.nakamura.api.profile.ProfileConstants.USER_IDENTIFIER_PROPERTY;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.scr.annotations.Services;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.events.EventDeliveryConstants;
import org.sakaiproject.nakamura.api.events.EventDeliveryConstants.EventDeliveryMode;
import org.sakaiproject.nakamura.api.events.EventDeliveryConstants.EventMessageMode;
import org.sakaiproject.nakamura.api.message.MessageProfileWriter;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.api.message.MessageTransport;
import org.sakaiproject.nakamura.api.message.MessagingService;
import org.sakaiproject.nakamura.api.personal.PersonalUtils;
import org.sakaiproject.nakamura.email.outgoing.OutgoingEmailMessageListener;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for notices. Needs to be started immediately to make sure it
 * registers with JCR as soon as possible.
 */

/**
 * Handler for notice messages.
 */
@Component(label = "MyBerkeley :: NoticeHandler", description = "Handler for internally delivered notices.", immediate = true, metatype = true)
@Services(value = { @Service(value = MessageTransport.class), @Service(value = MessageProfileWriter.class) })
@Properties(value = { @org.apache.felix.scr.annotations.Property(name = "service.vendor", value = "University of California, Berkeley"),
        @org.apache.felix.scr.annotations.Property(name = "service.description", value = "Handler for internally delivered notice messages") })
public class NoticeHandler implements MessageTransport, MessageProfileWriter {
    private static final Logger LOG = LoggerFactory.getLogger(NoticeHandler.class);

    @Reference
    protected transient SlingRepository slingRepository;

    @Reference
    protected transient MessagingService messagingService;

    @Reference
    protected transient MessageProfileWriter messageProfileWriter;
    
    @Reference
    protected transient EventAdmin eventAdmin;

    @org.apache.felix.scr.annotations.Property(value = { "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy" })
    private static final String PROP_DATE_FORMAT = "notice.dateFormats";

    private List<DateFormat> dateFormats = new LinkedList<DateFormat>();
    
    @org.apache.felix.scr.annotations.Property(boolValue = true)
    private static final String PROP_SEND_EMAIL = "notice.sendEmail";
    
    private boolean sendEmail;

    /**
     * {@inheritDoc}
     * 
     * @see org.sakaiproject.nakamura.api.message.MessageTransport#send(org.sakaiproject.nakamura.api.message.MessageRoutes,
     *      org.osgi.service.event.Event, javax.jcr.Node)
     */
    public void send(MessageRoutes routes, Event event, Node originalNotice) {
        long startMillis = System.currentTimeMillis();
        try {
            Session session = slingRepository.loginAdministrative(null);
            String rcpt = null;
            for (MessageRoute route : routes) {
                if (NOTICE_TRANSPORT.equals(route.getTransport())) {

                    rcpt = route.getRcpt();
                    Set<String> recipients = null;
                    LOG.info("Started a notice routing to: " + rcpt);
                    if (rcpt.trim().startsWith(DYNAMIC_LISTS_PREFIX)) {
                        Node targetListQueryNode = findTargetListQuery(rcpt, originalNotice, session);
                        recipients = findRecipients(rcpt, originalNotice, targetListQueryNode, session);
                        for (Iterator<String> iterator = recipients.iterator(); iterator.hasNext();) {
                            String recipient = (String) iterator.next();
                            long sendMessageStartMillis = System.currentTimeMillis();
                            sendNotice(recipient, originalNotice, event, session);
                            long sendMessageEndMillis = System.currentTimeMillis();
                            if (LOG.isDebugEnabled()) LOG.debug("send() total send millis " + (sendMessageEndMillis - sendMessageStartMillis));
                        }
                        if (this.sendEmail) sendEmail(recipients, event, originalNotice);
                    }
                    else {
                        sendNotice(rcpt, originalNotice, event, session);
                        recipients = new HashSet<String>();
                        recipients.add(rcpt);
                        if (this.sendEmail) sendEmail(recipients, event, originalNotice);
                    }
                }
            }
        }
        catch (RepositoryException e) {
            LOG.error(e.getMessage(), e);
        }
        finally {
            long endMillis = System.currentTimeMillis();
            if (LOG.isDebugEnabled()) LOG.debug("NoticeHandler.send() execution milliseconds: " + (endMillis - startMillis));
        }
    }

    /**
     * find the list that this notice is being sent to.
     * sakai:sendto=notice:${id} id must the the list id.
     * 
     * @param rcpt
     * @param originalNotice
     * @param session
     * @return
     * @throws RepositoryException
     */
    private Node findTargetListQuery(String rcpt, Node originalNotice, Session session) throws RepositoryException {
        Node targetListQuery = null;
        Node listsNode = getDynamicLists(originalNotice, session);
        NodeIterator listIter = listsNode.getNodes();
        while (listIter.hasNext()) {
            Node listNode = listIter.nextNode();
            String thisListId = listNode.getProperty(PROP_SAKAI_ID).getString();
            if (thisListId.equals(rcpt.trim())) {
                targetListQuery = listNode.getNode(DYNAMIC_LISTS_QUERY);
            }
        }
        return targetListQuery;
    }

    /**
     * retrieve the dynamic_links node
     * 
     * @param originalNotice
     * @param session
     * @return
     * @throws RepositoryException
     */
    private Node getDynamicLists(Node originalNotice, Session session) throws RepositoryException {
        Property senderIdProp = originalNotice.getProperty(PROP_SAKAI_FROM);
        String senderId = senderIdProp.getValue().getString();
        Authorizable au = PersonalUtils.getAuthorizable(session, senderId);
        String homePath = PersonalUtils.getHomeFolder(au);
        StringBuilder linksPathSB = new StringBuilder(homePath).append("/private/").append(DYNAMIC_LISTS_ROOT_NODE_NAME).append("/").append(
                DYNAMIC_LISTS_DATA_NODE_NAME);
        LOG.debug("loading dynamic lists from " + linksPathSB.toString());
        Node listsNode = session.getNode(linksPathSB.toString());
        return listsNode;
    }

    /**
     * send a notice to one "real user" as the dynamic list has been expanded at
     * this poing
     * 
     * @param recipient
     * @param originalNotice
     * @param event 
     * @param session
     */
    private void sendNotice(String recipient, Node originalNotice, Event event, Session session) {
        // the path were we want to save messages in.
        String messageId;
        try {
            messageId = originalNotice.getProperty(PROP_SAKAI_ID).getString();
            String toPath = messagingService.getFullPathToMessage(recipient, messageId, session);

            // Copy the node into the recipient's folder.
            JcrUtils.deepGetOrCreateNode(session, toPath.substring(0, toPath.lastIndexOf("/")));
            session.save();
            long copyStartMillis = System.currentTimeMillis();
            session.getWorkspace().copy(originalNotice.getPath(), toPath);
            Node n = JcrUtils.deepGetOrCreateNode(session, toPath);
            long copyEndMillis = System.currentTimeMillis();
            if (LOG.isDebugEnabled()) {
                LOG.debug("sendNotice() created recipient mesaage node: " + n.toString());
                LOG.debug("sendNotice() copying notice node execution milliseconds: " + (copyEndMillis - copyStartMillis));
            }
            javax.jcr.Property messageProp = originalNotice.getProperty(PROP_SAKAI_CATEGORY);
            String messageCategory = messageProp.getString();
            if (SAKAI_CATEGORY_REMINDER.equals(messageCategory)) {
                // need to due this conversion issue on new recipient node
                handleRequiredDate(originalNotice, n);
            }
            // Add some extra properties on the just created node.
            n.setProperty(PROP_SAKAI_READ, false);
            n.setProperty(PROP_SAKAI_MESSAGEBOX, BOX_INBOX);
            n.setProperty(PROP_SAKAI_SENDSTATE, STATE_NOTIFIED);
            // only put the single recipient into this property, not all
            // of them
            n.setProperty(PROP_SAKAI_TO, recipient);

            if (session.hasPendingChanges()) {
                long saveStartMillis = System.currentTimeMillis();
                session.save();
                long saveEndMillis = System.currentTimeMillis();
                if (LOG.isDebugEnabled()) LOG.debug("sendNotice() saving session execution milliseconds: " + (saveEndMillis - saveStartMillis));
            }
        }
        catch (RepositoryException e) {
            LOG.error("sendNotice() failed", e);
        }

    }
    
    /**
     * {@inheritDoc}
     *
     * @see org.sakaiproject.nakamura.api.message.MessageTransport#send(org.sakaiproject.nakamura.api.message.MessageRoutes,
     *      org.osgi.service.event.Event, javax.jcr.Node)
     */
    protected void sendEmail(Set<String> recipients, Event event, Node n) {
      LOG.debug("Started handling an email message");

      if (recipients != null) {
        java.util.Properties props = new java.util.Properties();
        try {
          if ( event != null ) {
            for( String propName : event.getPropertyNames()) {
              Object propValue = event.getProperty(propName);
              props.put(propName, propValue);
            }
          }
          // make the message deliver to one listener, that means the desination must be a queue.
          props.put(EventDeliveryConstants.DELIVERY_MODE, EventDeliveryMode.P2P);
          // make the message persistent to survive restarts.
          props.put(EventDeliveryConstants.MESSAGE_MODE, EventMessageMode.PERSISTENT);
          // email listener wants a list
          props.put(OutgoingEmailMessageListener.RECIPIENTS, new ArrayList<String>(recipients));
          props.put(NODE_PATH_PROPERTY, n.getPath());
          Event emailEvent = new Event(QUEUE_NAME, props);

          LOG.debug("Sending event [" + emailEvent + "]");
          eventAdmin.postEvent(emailEvent);
        } catch (RepositoryException e) {
          LOG.error(e.getMessage(), e);
        }
      }
    }


    /**
     * find the users that meet the search criteria in the query subnode of the
     * dynamic_lists/list node
     * 
     * @param rcpt
     * @param originalNotice
     * @param queryNode
     * @param session
     * @return
     * @throws RepositoryException
     */
    private Set<String> findRecipients(String rcpt, Node originalNotice, Node queryNode, Session session) throws RepositoryException {
        Set<String> recipients = new HashSet<String>();
        String queryString = buildQuery(originalNotice, queryNode);
        // String queryString =
        // "/jcr:root//*[@sling:resourceType='sakai/user-profile']/myberkeley/elements/context[@value='g-ced-students']/../standing[@value='grad']/../major[@value='ARCHITECTURE' or @value='DESIGN']";
        if (LOG.isDebugEnabled()) LOG.info("findRecipients() Using Query {} ", queryString);
        // find all the notices in the queue for this advisor
        long startMillis = System.currentTimeMillis();
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        Query query = queryManager.createQuery(queryString, "xpath");
        QueryResult result = query.execute();
        long endMillis = System.currentTimeMillis();
        if (LOG.isDebugEnabled()) LOG.debug("NoticeHandler.findRecipients() execution milliseconds: " + (endMillis - startMillis));
        NodeIterator recpientIter = result.getNodes();
        Node contextNode = null;
        Node recipientProfileNode = null;
        String recipientId = null;
        while (recpientIter.hasNext()) {
            try {
                contextNode = recpientIter.nextNode();
                // now get the parent authprofile node that has the rep:userId
                // in it
                recipientProfileNode = contextNode.getNode("../../../");
                recipientId = recipientProfileNode.getProperty(USER_IDENTIFIER_PROPERTY).getString();
                recipients.add(recipientId);
            }
            catch (RepositoryException e) {
                LOG.error("findRecipients() failed for {}", new Object[] { recipientProfileNode.getPath() }, e);
            }
        }
        if (LOG.isDebugEnabled()) LOG.debug("Dynamic List Notice recipients are: " + recipients);
        return recipients;
    }

    /**
     * build an xpath query from the list query node subnodes and values
     * 
     * @param originalNotice
     * @param queryNode
     * @return
     * @throws RepositoryException
     */

    private String buildQuery(Node originalNotice, Node queryNode) throws RepositoryException {
        StringBuilder querySB = new StringBuilder(
                "/jcr:root//*[@sling:resourceType='sakai/user-profile']/myberkeley/elements/current[@value='true']/..");
        NodeIterator queryNodesIter = queryNode.getNodes();
        while (queryNodesIter.hasNext()) {
            Node queryParamNode = queryNodesIter.nextNode();
            String paramName = queryParamNode.getName();
            Node paramNode = queryNode.getNode(paramName);
            querySB.append("/").append(paramName);
            PropertyIterator paramValuePropsIter = paramNode.getProperties();
            Set<String> paramValues = new HashSet<String>();

            // need to copy Properties into array because jcr:primaryType
            // property breaks isLastValue
            while (paramValuePropsIter.hasNext()) {
                Property prop = paramValuePropsIter.nextProperty();
                if (prop.getName().startsWith("__array")) {
                    String paramValue = prop.getValue().getString();
                    paramValue = new StringBuffer("'").append(paramValue).append("'").toString();
                    paramValues.add(paramValue);
                }
            }
            boolean isFirstValue = true;
            boolean isLastValue = false;
            for (Iterator<String> paramValuesIter = paramValues.iterator(); paramValuesIter.hasNext();) {
                String paramValue = paramValuesIter.next();
                if (!paramValuesIter.hasNext())
                    isLastValue = true;
                addParamToQuery(querySB, paramName, paramValue, isFirstValue, isLastValue);
                isFirstValue = false;
            }
            if (queryNodesIter.hasNext())
                querySB.append("/..");
        }
        return querySB.toString();
    }

    private void addParamToQuery(StringBuilder querySB, String paramName, String paramValue, boolean isFirstValue, boolean isLastValue) {
        if (isFirstValue)
            querySB.append("[");
        querySB.append("@value=").append(paramValue);
        if (!isLastValue) {
            querySB.append(" or ");
        }
        else {
            querySB.append("]");
        }
    }

    /**
     * method to make sure the recipient notice node stores a Date object
     * sometimes the originalNotice has a String and sometimes a Date at this
     * point, assuming that's a concurrency issue with the SlingPostServlet's
     * date parsing running before or after this method. Haven't tracked it down
     * exactly but this logic fixes the issue making sure the recipient notice
     * has a Date dueDate or a Date eventDate. enforces necessity of having
     * either a dueDate or an eventDate
     * 
     * @param originalNotice
     * @param newNotice
     * @throws PathNotFoundException
     * @throws RepositoryException
     */
    private void handleRequiredDate(Node originalNotice, Node newNotice) throws PathNotFoundException, RepositoryException {
        javax.jcr.Property dueDateProp = null;
        javax.jcr.Property eventDateProp = null;
        Calendar requiredDate = null;
        if (originalNotice.hasProperty(PROP_SAKAI_DUEDATE)) {
            dueDateProp = originalNotice.getProperty(PROP_SAKAI_DUEDATE);
            if (LOG.isDebugEnabled()) LOG.debug("handleRequiredDate() got source dueDateProp: " + dueDateProp);
            requiredDate = buildRequiredDate(dueDateProp);
            if (LOG.isDebugEnabled()) LOG.debug("setting {} to {} in newNotice {}", new Object[] { PROP_SAKAI_DUEDATE, requiredDate.getTime(), newNotice.getPath() });
            newNotice.setProperty(PROP_SAKAI_DUEDATE, requiredDate);
        }
        else if (originalNotice.hasProperty(PROP_SAKAI_EVENTDATE)) {
            eventDateProp = originalNotice.getProperty(PROP_SAKAI_EVENTDATE);
            if (LOG.isDebugEnabled()) LOG.debug("handleRequiredDate() got source eventDateProp: " + eventDateProp);
            requiredDate = buildRequiredDate(eventDateProp);
            if (LOG.isDebugEnabled()) LOG.debug("setting {} to {} in newNotice {}", new Object[] { PROP_SAKAI_EVENTDATE, requiredDate.getTime(), newNotice.getPath() });
            newNotice.setProperty(PROP_SAKAI_EVENTDATE, requiredDate);
        }
        else {
            StringBuilder sb = new StringBuilder("handleRequiredDate() A required Reminder must have either a ").append(PROP_SAKAI_DUEDATE).append(" or a ")
                    .append(PROP_SAKAI_EVENTDATE).append(", not sending Notice ").append(originalNotice.getPath());
            throw new RepositoryException(sb.toString());
        }
    }

    private Calendar buildRequiredDate(Property requiredDateProp) throws RepositoryException {
        Calendar requiredDateCal = null;
        Value value = requiredDateProp.getValue();
        int requiredDateValueType = value.getType();
        if (LOG.isDebugEnabled()) LOG.debug("handleRequiredDate() got source requiredDateProp Value Type: " + requiredDateValueType);
        if (PropertyType.STRING == requiredDateValueType) {
            requiredDateCal = parse(value.getString());
        }
        else if (PropertyType.DATE == requiredDateValueType) {
            requiredDateCal = requiredDateProp.getDate();
        }
        else {
            String message = "handleRequiredDate() requiredDate is not a String or Date, is PropertyType: " + requiredDateValueType + " cannot handle";
            throw new RepositoryException(message);
        }
        return requiredDateCal;
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
        return TYPE_NOTICE;
    }

    /**
     * delegate to the InternalMessageHandler for now {@inheritDoc}
     * 
     * @see org.sakaiproject.nakamura.api.message.MessageProfileWriter#writeProfileInformation(javax.jcr.Session,
     *      java.lang.String, org.apache.sling.commons.json.io.JSONWriter)
     */
    public void writeProfileInformation(Session session, String recipient, JSONWriter write) {
        // can't write a profile as dynamic list doesn't have a profile
        if (recipient != null && !recipient.startsWith(DYNAMIC_LISTS_PREFIX)) {
            this.messageProfileWriter.writeProfileInformation(session, recipient, write);
        }
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
    
    protected void bindEventAdmin(EventAdmin eventAdmin) {
    	this.eventAdmin = eventAdmin;
    }
    
    protected void unbindEventAdmin(EventAdmin eventAdmin) {
    	this.eventAdmin = null;
    }    

    protected void activate(ComponentContext context) {
        Dictionary<?, ?> props = context.getProperties();
        DateFormat dateFormat = null;
        String[] dateFormatStrs = OsgiUtil.toStringArray(props.get(PROP_DATE_FORMAT));
        for (String dateFormatStr : dateFormatStrs) {
            dateFormat = new SimpleDateFormat(dateFormatStr, Locale.US);
            this.dateFormats.add(dateFormat);
        }
        this.sendEmail = (Boolean) props.get(PROP_SEND_EMAIL);
    }

    protected void deactivate(ComponentContext context) {
        this.dateFormats = null;
        this.sendEmail = false;
    }
}
