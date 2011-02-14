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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import javax.jcr.ValueFormatException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang.builder.ToStringBuilder;
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
import org.sakaiproject.nakamura.email.outgoing.OutgoingEmailMessageListener;
import org.sakaiproject.nakamura.util.JcrUtils;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
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
    
    @org.apache.felix.scr.annotations.Property(value = { "standing" })
    private static final String PROP_NESTED_QUERY_PARAMS = "notice.nestedQueryParams";
    
    private Set<String> nestedQueryParams = new HashSet<String>();
    
    @org.apache.felix.scr.annotations.Property(value = "context")
    private static final String PROP_ANCHOR_QUERY_PARAM = "notice.anchorQueryParam";
    
    private String anchorQueryParam;

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
                    Set<Recipient> recipients = null;
                    LOG.info("Started a notice routing to: " + rcpt);
                    if (rcpt.trim().startsWith(DYNAMIC_LISTS_PREFIX)) {
                        Node targetListQueryNode = findTargetListQuery(rcpt, originalNotice, session);
                        recipients = findRecipients(rcpt, originalNotice, targetListQueryNode, session);
                        for (Iterator<Recipient> iterator = recipients.iterator(); iterator.hasNext();) {
                            Recipient recipient = iterator.next();
                            long sendMessageStartMillis = System.currentTimeMillis();
                            sendNotice(recipient.getRecipientId(), originalNotice, event, session);
                            long sendMessageEndMillis = System.currentTimeMillis();
                            if (LOG.isDebugEnabled())
                                LOG.debug("send() total send millis " + (sendMessageEndMillis - sendMessageStartMillis));
                        }
                        List<String> emailRecipientIds = new ArrayList<String>();
                        Recipient recipient = null;
                        if (isReminder(originalNotice)) {
                            for (Iterator<Recipient> iterator = recipients.iterator(); iterator.hasNext();) {
                                recipient = iterator.next();
                                if (recipient.isCurrentParticipant()) {
                                    emailRecipientIds.add(recipient.getRecipientId());
                                }
                            }
                            sendEmail(emailRecipientIds, event, originalNotice);
                        }
                        else {
                            LOG.info("Notification {} is not a reminder, not sending email", new Object[]{originalNotice.getPath()});
                        }
                    }
                    else {
                        sendNotice(rcpt, originalNotice, event, session);
                        List<String> emailRecipientIds = new ArrayList<String>();
                        emailRecipientIds.add(rcpt);
                        sendEmail(emailRecipientIds, event, originalNotice);
                    }
                }
            }
        }
        catch (RepositoryException e) {
            LOG.error(e.getMessage(), e);
        }
        finally {
            long endMillis = System.currentTimeMillis();
            if (LOG.isDebugEnabled())
                LOG.debug("NoticeHandler.send() execution milliseconds: " + (endMillis - startMillis));
        }
    }

    private boolean isReminder(Node originalNotice) {
        boolean isReminder = false;
        String noticeType, noticePath = null;
        try {
            noticePath = originalNotice.getPath();
            noticeType = originalNotice.getProperty(PROP_SAKAI_CATEGORY).getString();
            LOG.debug("notification {} has a {} of {}", new Object[] { noticePath, PROP_SAKAI_CATEGORY, noticeType });
            if (SAKAI_CATEGORY_REMINDER.equalsIgnoreCase(noticeType)) {
                isReminder = true;
            }
        }
        catch (ValueFormatException e) {
            LOG.error("can't determine {} for notification: {}", new Object[]{PROP_SAKAI_CATEGORY, noticePath}, e);
        }
        catch (PathNotFoundException e) {
            LOG.error("can't determine {} for notification: {}", new Object[]{PROP_SAKAI_CATEGORY, noticePath}, e);
        }
        catch (RepositoryException e) {
            LOG.error("can't determine {} for notification: {}", new Object[]{PROP_SAKAI_CATEGORY, noticePath}, e);
        }
        return isReminder;
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
        String homePath = LitePersonalUtils.getHomePath(senderId);
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
     * 
     * @param recipients
     * @param event
     * @param n
     */
    protected void sendEmail(List<String> recipients, Event event, Node n) {
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
          props.put(OutgoingEmailMessageListener.RECIPIENTS, recipients);
          props.put(NODE_PATH_PROPERTY, n.getPath());
          Event emailEvent = new Event(QUEUE_NAME, props);

          LOG.debug("Sending event [" + emailEvent + "]");
          eventAdmin.sendEvent(emailEvent);
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
    private Set<Recipient> findRecipients(String rcpt, Node originalNotice, Node queryNode, Session session) throws RepositoryException {
//        String queryString = buildQuery(originalNotice, queryNode);
        // String queryString =
        // "/jcr:root//*[@sling:resourceType='sakai/user-profile']/myberkeley/elements/context[@value='g-ced-students']/../standing[@value='grad']/../major[@value='ARCHITECTURE' or @value='DESIGN']";
        // find all the notices in the queue for this advisor
        // need to do two queries, one for each standing as there doesn't seem to be a way to put "or" logic into the path spec as there is in the attributes
        // this is now hardwired to standings of 'grad' and 'undergrad'
        String queryString = null;
        Set<Recipient> recipients = new HashSet<Recipient>();
        DynamicListQueryParamExtractor extractor = new MyBerkeleyDynamicListQueryParamExtractor(queryNode, this.anchorQueryParam, this.nestedQueryParams);
        Node anchorNode = extractor.getAnchorNode();
        Set<String> multipleQueryValues = extractor.getMultipleQueryValues();
        for (Iterator<String> iterator = multipleQueryValues.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            String[] queryKeyParams = extractor.getQueryKeyParams(value);
            Set<String> queryValues = extractor.getQueryValues(value);
            ProfileQueryBuilder queryBuilder = new MyBerkeleyProfileQueryBuilder();
            queryBuilder.appendRoot("/jcr:root//*[@sling:resourceType='sakai/user-profile']/myberkeley/elements/current[@value='true']/..")
                        .appendAnchorNodeParam(anchorNode)
                        .appendNestedNodeParams(queryKeyParams, queryValues);
            queryString = queryBuilder.toString();
            if (LOG.isDebugEnabled()) LOG.debug("findRecipients() Using Query {} ", new Object[] { queryString });
            addRecipients(queryString, recipients, session);
            if (LOG.isDebugEnabled()) LOG.debug("after query execution recipients are: {}", new Object[] { recipients });
        }
        return recipients;
    }

    private void addRecipients(String queryString, Set<Recipient> recipients, Session session) throws RepositoryException {
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
                boolean isCurrentParticipant = isCurrentParticipant(contextNode);
                Recipient recipient = new Recipient(recipientId, isCurrentParticipant);
                recipients.add(recipient);
            }
            catch (RepositoryException e) {
                LOG.error("findRecipients() failed for {}", new Object[] { recipientProfileNode.getPath() }, e);
                throw e;
            }
        }
    }

    private boolean isCurrentParticipant(Node contextNode) {
    	boolean isCurrentParticipant = false;
		try {
			Node participantNode = contextNode.getNode("../participant");
			String value = participantNode.getProperty("value").getString();
			isCurrentParticipant = value != null && "true".equals(value) ? true : false;
		} catch (PathNotFoundException e) {
			e.printStackTrace();
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return isCurrentParticipant;
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
    
    private final class Recipient {
        private String recipientId = "";
        private boolean currentParticipant = false;
        
        private Recipient(String recipientId, boolean currentParticipant) {
            this.recipientId = recipientId;
            this.currentParticipant = currentParticipant;
        }

        public String getRecipientId() {
            return recipientId;
        }

        public boolean isCurrentParticipant() {
            return currentParticipant;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;

            result = prime * result
                    + ((recipientId == null) ? 0 : recipientId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Recipient other = (Recipient) obj;
            if (recipientId == null) {
                if (other.recipientId != null)
                    return false;
            } else if (!recipientId.equals(other.recipientId))
                return false;
            return true;
        }

        @Override
        public String toString() {
              return new ToStringBuilder(this).
                append("recipientId", recipientId).
                append("currentParticipant", currentParticipant).
                toString();
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
        String[] nestedQueryParams = OsgiUtil.toStringArray(props.get(PROP_NESTED_QUERY_PARAMS));
        for (int i = 0; i < nestedQueryParams.length; i++) {
            this.nestedQueryParams.add(nestedQueryParams[i]);
        }
        this.anchorQueryParam = (String) props.get(PROP_ANCHOR_QUERY_PARAM);
    }

    protected void deactivate(ComponentContext context) {
        this.dateFormats = null;
    }
    
}
