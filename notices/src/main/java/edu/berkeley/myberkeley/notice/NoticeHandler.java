package edu.berkeley.myberkeley.notice;

import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_DATA_NODE_NAME;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_PREFIX;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_QUERY;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.DYNAMIC_LISTS_ROOT_NODE_NAME;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.NOTICE_TRANSPORT;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_CATEGORY;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_DUEDATE;
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
import org.sakaiproject.nakamura.api.message.MessageProfileWriter;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.api.message.MessageTransport;
import org.sakaiproject.nakamura.api.message.MessagingService;
import org.sakaiproject.nakamura.api.personal.PersonalUtils;
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
@Component(label = "MyBerkeley :: NoticeHandler", description = "Handler for internally delivered notices.", immediate = true, metatype=true)
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
    

    @org.apache.felix.scr.annotations.Property(value = { "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss",
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
            String rcpt = null;
            for (MessageRoute route : routes) {
                if (NOTICE_TRANSPORT.equals(route.getTransport())) {
                    
                    rcpt = route.getRcpt();
                    LOG.info("Started a notice routing to: " + rcpt);
                    if (rcpt.trim().startsWith(DYNAMIC_LISTS_PREFIX)) {
                        Node targetListQueryNode = findTargetListQuery(rcpt, originalNotice, session);
                        Set<String> recipients = findRecipients(rcpt, originalNotice, targetListQueryNode, session);
                        for (Iterator<String> iterator = recipients.iterator(); iterator.hasNext();) {
                            String recipient = (String) iterator.next();
                            sendNotice(recipient, originalNotice, session);
                        }
                    }
                    else {
                        sendNotice(rcpt, originalNotice, session);
                    }
                }
            }
        }
        catch (RepositoryException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * find the list that this notice is being sent to.  sakai:sendto=notice:${id}
     * id must the the list id.
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
            String thisListId = listNode.getProperty("id").getString();
            if (thisListId.equals(rcpt.trim())) {
                targetListQuery = listNode.getNode(DYNAMIC_LISTS_QUERY);
            }
        }
        return targetListQuery;
    }

    /**
     * retrieve the dynamic_links node
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
        StringBuilder linksPathSB = new StringBuilder(homePath)
                                    .append("/private/")
                                    .append(DYNAMIC_LISTS_ROOT_NODE_NAME)
                                    .append("/")
                                    .append(DYNAMIC_LISTS_DATA_NODE_NAME);
        LOG.debug("loading dynamic lists from " + linksPathSB.toString());
        Node listsNode = session.getNode(linksPathSB.toString());
        return listsNode;
    }

    /**
     * send a notice to one "real user" as the dynamic list has been expanded
     * at this poing
     * @param recipient
     * @param originalNotice
     * @param session
     */
    private void sendNotice(String recipient, Node originalNotice, Session session) {
        // the path were we want to save messages in.
        String messageId;
        try {
            messageId = originalNotice.getProperty(PROP_SAKAI_ID).getString();
            String toPath = messagingService.getFullPathToMessage(recipient, messageId, session);

            // Copy the node into the recipient's folder.
            JcrUtils.deepGetOrCreateNode(session, toPath.substring(0, toPath.lastIndexOf("/")));
            session.save();
            session.getWorkspace().copy(originalNotice.getPath(), toPath);
            Node n = JcrUtils.deepGetOrCreateNode(session, toPath);
            LOG.debug("created recipient mesaage node: " + n.toString());
            javax.jcr.Property messageProp = originalNotice.getProperty(PROP_SAKAI_CATEGORY);
            String messageCategory = messageProp.getString();
            if (SAKAI_CATEGORY_REMINDER.equals(messageCategory)) {
                // need to due this conversion issue on new recipient node
                handleDueDate(originalNotice, n);
            }
            // Add some extra properties on the just created node.
            n.setProperty(PROP_SAKAI_READ, false);
            n.setProperty(PROP_SAKAI_MESSAGEBOX, BOX_INBOX);
            n.setProperty(PROP_SAKAI_SENDSTATE, STATE_NOTIFIED);
            // only put the single recipient into this property, not all
            // of them
            n.setProperty(PROP_SAKAI_TO, recipient);

            if (session.hasPendingChanges()) {
                session.save();
            }
        }
        catch (RepositoryException e) {
            LOG.error("sendNotice() failed", e);
        }

    }

    /**
     * find the users that meet the search criteria in the query subnode of the dynamic_lists/list node
     * @param rcpt
     * @param originalNotice
     * @param queryNode
     * @param session
     * @return
     * @throws RepositoryException
     */
    private Set<String> findRecipients(String rcpt, Node originalNotice, Node queryNode, Session session) throws  RepositoryException {
        Set<String> recipients = new HashSet<String>();
//        String queryString = buildQuery(originalNotice, queryNode);
        String queryString =   "/jcr:root//*[@sling:resourceType='sakai/user-profile' and (jcr:contains(@sakai:context, 'g-ced-students')) and (jcr:contains(@sakai:major, '\"ARCHITECTURE\" OR \"ENV DESIGN\"'))]";
        // /jcr:root/_user/_x0032_/_x0032_0/_x0032_040/message//*[@sling:resourceType="sakai/message"
        // and @sakai:type="notice and @sakai:messagebox="sakai:queue
        
        LOG.info("findQueuedNotices() Using Query {} ", queryString);
        // find all the notices in the queue for this advisor
        QueryManager queryManager = session.getWorkspace().getQueryManager();
        Query query = queryManager.createQuery(queryString, "xpath");
        QueryResult result = query.execute();
        NodeIterator recpientIter = result.getNodes();
        Node recipientProfileNode = null;
        String recipientId = null;
        while (recpientIter.hasNext()) {
            try {
                recipientProfileNode = recpientIter.nextNode();
                recipientId = recipientProfileNode.getProperty("rep:userId").getString();
                recipients.add(recipientId);                
            }
            catch (RepositoryException e) {
                LOG.error("findRecipients() failed for {}", new Object[]{recipientProfileNode.getPath()}, e);
            }
        }
        LOG.debug("Dynamic List Notice recipients are: " + recipients);
        return recipients;
    }
    
    /**
     * build an xpath query from the list query node subnodes and values
     * @param originalNotice
     * @param queryNode
     * @return
     * @throws RepositoryException
     */

    private String buildQuery(Node originalNotice, Node queryNode) throws RepositoryException{
        StringBuilder querySB = new StringBuilder( );
        NodeIterator queryNodesIter = queryNode.getNodes();
        while (queryNodesIter.hasNext()) {
            Node queryParamNode = queryNodesIter.nextNode();
            String paramName = queryParamNode.getName();
            Node paramNode = queryNode.getNode(paramName);
            querySB.append(" and "); 
            PropertyIterator paramValuePropsIter = paramNode.getProperties();
            Set<String> paramValues = new HashSet<String>();

            // need to copy Properties into array because jcr:primaryType property breaks isLastValue
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
        }
        querySB.append("]");
        return querySB.toString();
    }     

    private void addParamToQuery(StringBuilder querySB, String paramName, String paramValue, boolean isFirstValue, boolean isLastValue) {
        if (isFirstValue) querySB.append("(");
        querySB.append("@sakai:").append(paramName).append(" = ").append(paramValue);
        if (!isLastValue) {
            querySB.append(" or ");
        }
        else {
            querySB.append(")");
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
        javax.jcr.Property dueDateProp = originalNotice.getProperty(PROP_SAKAI_DUEDATE);
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
                LOG.debug("setting " + PROP_SAKAI_DUEDATE + " to " + senderDueDate.getTime().toString() + " in newNotice: "
                        + newNotice.toString());
                newNotice.setProperty(PROP_SAKAI_DUEDATE, senderDueDate);
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
        return TYPE_NOTICE;
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
        this.dateFormats = null;
    }
}
