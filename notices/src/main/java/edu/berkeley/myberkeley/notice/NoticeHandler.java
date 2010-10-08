package edu.berkeley.myberkeley.notice;

import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.GROUP_CED_STUDENTS;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.NOTICE_TRANSPORT;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_CATEGORY;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.PROP_SAKAI_DUEDATE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.SAKAI_CATEGORY_REMINDER;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.TYPE_NOTICE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.BOX_INBOX;
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
import java.util.Arrays;
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
import org.apache.jackrabbit.util.ISO9075;
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
    
    @org.apache.felix.scr.annotations.Property(value = { "sakai:context", "sakai:standing", "sakai:major1", "sakai:major2"}, description = "the dynamic list query parameter names")
    private static final String PROP_QUERY_PARAM_NAMES = "notice.queryParamNames"; 
    
    private List<String> queryParamNames;
    
    @org.apache.felix.scr.annotations.Property(value = { "sakai:major2"}, description = "query parameter names which can be missing or void")
    private static final String PROP_OPTIONAL_QUERY_PARAM_NAMES = "notice.optionalQueryParamNames"; 
    
    private List<String> optionalQueryParamNames;
    
    @org.apache.felix.scr.annotations.Property(value = {GROUP_CED_STUDENTS}, description = "the target groups for dynamic list queries")
    private static final String PROP_DYNAMIC_LIST_GROUPS = "notice.dynamicListGroups";   
    
    private List<String> dynamicListGroups;

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
                    LOG.info("Started a notice routing.");
                    // put the group recipient expansion here
                    rcpt = route.getRcpt();
                    if (this.dynamicListGroups.contains(rcpt.trim())) {
                        Set<String> recipients = findRecipients(rcpt, originalNotice, session);
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

    private Set<String> findRecipients(String rcpt, Node originalNotice, Session session) throws  RepositoryException {
        Set<String> recipients = new HashSet<String>();
        Property senderIdProp = originalNotice.getProperty(MessageConstants.PROP_SAKAI_FROM);
        String senderId = senderIdProp.getValue().getString();
        String messageStorePath = ISO9075.encodePath(messagingService.getFullPathToStore(senderId, session));
        
        String queryString = buildQuery(originalNotice);
        
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
        return recipients;
    }

    private String buildQuery(Node originalNotice) throws RepositoryException{
        StringBuilder querySB = new StringBuilder("/jcr:root//*[@sling:resourceType='sakai/user-profile'");
        for (Iterator<String> iterator = this.queryParamNames.iterator(); iterator.hasNext();) {
            String paramName = iterator.next();
            Property paramProp = originalNotice.getProperty(paramName);
            boolean isOptionalParam = this.optionalQueryParamNames.contains(paramName.trim());
            if (paramProp != null) {
                if (isOptionalParam) {
                    querySB.append(" or ");                                        
                }
                else {
                    querySB.append(" and ");                    
                }
                String paramValue = null;
                if (paramProp.isMultiple()) {
                    boolean isFirstValue = true;
                    boolean isLastValue = false;
                    Value[] paramVals = paramProp.getValues();
                    for (int i = 0; i < paramVals.length; i++) {
                        if (i > 0) isFirstValue = false;
                        if (i == paramVals.length - 1) isLastValue = true;
                        paramValue = paramVals[i].getString();
                        addParamToQuery(querySB, paramName, paramValue, isFirstValue, isLastValue);
                    }
                }
                else {
                    paramValue = paramProp.getValue().getString();
                    addParamToQuery(querySB, paramName, paramValue, true, true);
                }
            }
            else if (!isOptionalParam) {
                StringBuilder sb = new StringBuilder("buildQuery() required query parameter: ")
                                    .append(paramName)
                                    .append(" is missing from notice: ")
                                    .append(originalNotice.getPath())
                                    .append(" cannot find recipients to send notice");
                throw new RepositoryException(sb.toString());
            }
        }
        querySB.append("]");
        return querySB.toString();
    }

    private void addParamToQuery(StringBuilder querySB, String paramName, String paramValue, boolean isFirstValue, boolean isLastValue) {
        if (isFirstValue) querySB.append("(");
        querySB.append("@").append(paramName).append(" = ").append(paramValue);
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
        String[] queryParamNamesArr = OsgiUtil.toStringArray(props.get(PROP_QUERY_PARAM_NAMES));
        this.queryParamNames = Arrays.asList(queryParamNamesArr);
        String[] optionalQueryParamNamesArr = OsgiUtil.toStringArray(props.get(PROP_OPTIONAL_QUERY_PARAM_NAMES));
        this.optionalQueryParamNames = Arrays.asList(optionalQueryParamNamesArr);
        String[] dynamicListGroupsArr = OsgiUtil.toStringArray(props.get(PROP_DYNAMIC_LIST_GROUPS));
        this.dynamicListGroups = Arrays.asList(dynamicListGroupsArr);
    }

    protected void deactivate(ComponentContext context) {
        this.dateFormats = null;
        this.queryParamNames = null;
        this.optionalQueryParamNames = null;
        this.dynamicListGroups = null;
    }
}
