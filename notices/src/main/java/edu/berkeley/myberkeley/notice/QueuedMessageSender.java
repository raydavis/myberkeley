package edu.berkeley.myberkeley.notice;

import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.BOX_ARCHIVE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.BOX_QUEUE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.GROUP_CED_ADVISORS;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.STATE_SEND_FAILED;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.TYPE_NOTICE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.BOX_OUTBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.EVENT_LOCATION;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PENDINGMESSAGE_EVENT;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_MESSAGEBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_SENDSTATE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_TYPE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.STATE_NONE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.STATE_NOTIFIED;
import static org.sakaiproject.nakamura.api.message.MessageConstants.STATE_PENDING;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.message.MessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants;

@Component(label = "MyBerkeley :: QueuedMessageSender", description = "Sends queued messages when they reach their send date", immediate = true, metatype=true)
@Service(value = edu.berkeley.myberkeley.notice.QueuedMessageSender.class)
public class QueuedMessageSender {

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Reference
    protected transient SlingRepository repository;

    @Reference
    protected transient Scheduler scheduler;

    @Reference
    protected transient EventAdmin eventAdmin;

    @Reference
    protected transient MessagingService messagingService;
    
    @org.apache.felix.scr.annotations.Property(longValue = 30, label="Poll Interval Seconds")
    private static final String PROP_POLL_INTERVAL_SECONDS = "queuedsender.pollinterval";
    
    @org.apache.felix.scr.annotations.Property(boolValue = false, label="Run Now")
    private static final String PROP_RUN_NOW = "queuedsender.runnow";
    
    protected final static String JOB_NAME = "sendQueuedNoticesJob";
    
    @org.apache.felix.scr.annotations.Property(value = "prod", label="Runtime Environment")
    private static final String PROP_RUNTIME_ENVIRONMENT = "queuedsender.environment"; 
    
    private static final String ENVVIRONMENT_DEV = "dev";    
    
    private static final String DEV_USER_ID = "271592"; 
    
    protected void activate(ComponentContext componentContext) throws Exception {
        // executes the job every minute
//        String schedulingExpression = "0 * * * * ?";
        Dictionary<?, ?> props = componentContext.getProperties();
        Long pollInterval = (Long) props.get(PROP_POLL_INTERVAL_SECONDS);
        Boolean runNow = (Boolean) props.get(PROP_RUN_NOW);
        String runtimeEnvironment= (String) props.get(PROP_RUNTIME_ENVIRONMENT);
        Map<String, Serializable> config = new HashMap<String, Serializable>();
        config.put(PROP_RUNTIME_ENVIRONMENT, runtimeEnvironment);
        boolean canRunConcurrently = true;
        final Job sendQueuedNoticeJob = new SendQueuedNoticesJob();
        try {
            if (!runNow) {
                this.scheduler.addPeriodicJob(JOB_NAME, sendQueuedNoticeJob, config, pollInterval, false);
            }
            else {
                this.scheduler.fireJob(sendQueuedNoticeJob, config);
            }
        }
        catch (Exception e) {
            LOGGER.error("sendQueuedNoticesJob Failed", e);
        }
    }
    
    protected void dectivate(ComponentContext componentContext) throws Exception {
        this.scheduler.removeJob(JOB_NAME);
    }

    /**
     * A Scheduler Job that will send all notices for all members of the
     * g-ced-advisors group that are in the sakai:messagebox=queue whose
     * sakai:sendDate has come, then move all the notices into the
     * sakai:messagebox=archive
     * 
     * @author johnk
     * 
     */
    public class SendQueuedNoticesJob implements Job {
        public void execute(JobContext context) {
            LOGGER.info("Executing SendQueuedNoticesJob");
            Session adminSession = null;
            try {
                adminSession = repository.loginAdministrative(null);
                Map<String, Serializable> config =context.getConfiguration();
                String runtimeEnvironment = (String) config.get(PROP_RUNTIME_ENVIRONMENT);
                UserManager um = AccessControlUtil.getUserManager(adminSession);
                Authorizable au = um.getAuthorizable(GROUP_CED_ADVISORS);
                Iterator<Authorizable> membersIter;
                Authorizable userAuth;
                String advisorId;
                if (au == null) {
                    LOGGER.error("Group {} doesn't exist", new Object[]{GROUP_CED_ADVISORS});
                }
                else if (au.isGroup()) {
                    membersIter = ((Group) au).getMembers();
                    while (membersIter.hasNext()) {
                        userAuth = membersIter.next();
                        advisorId = userAuth.getID();
                        if (!ENVVIRONMENT_DEV.equals(runtimeEnvironment) || DEV_USER_ID.equals(advisorId)) {  // for development only
                            // notice messages live in individual authors
                            // message store currently
                            if (!userAuth.isGroup()) {
                                QueryResult queuedNoticesQR = findQueuedNotices(advisorId, adminSession);
                                NodeIterator noticeIter = queuedNoticesQR.getNodes();
                                Node notice = null;
                                while (noticeIter.hasNext()) {
                                    try {
                                        notice = noticeIter.nextNode();
                                        LOGGER.debug("at noticeIter position: {} found notice: {}",
                                                        new Object[] { noticeIter.getPosition(), notice.getPath() });
                                        if (timeToSend(notice)) {
                                            // move it to outbox for std sending
                                            // mechanism and so next search will not find it
                                            notice.setProperty(PROP_SAKAI_MESSAGEBOX, BOX_OUTBOX);
                                            if (adminSession.hasPendingChanges()) {
                                                adminSession.save();
                                            }
                                            sendNotice(notice, advisorId);
                                            // now more it archive box per user
                                            // interaction spec so it shows up in UI Archive
                                            notice.setProperty(PROP_SAKAI_MESSAGEBOX, BOX_ARCHIVE);
                                            if (adminSession.hasPendingChanges()) {
                                                adminSession.save();
                                            }
                                        }
                                    }
                                    catch (RepositoryException e) {
                                        // put in failed sendState so it won't be found again and repeated failures occur
                                        LOGGER.error("Could not send notice {}, putting in sendState: {}", new Object[]{notice.getPath(), STATE_SEND_FAILED});
                                        notice.setProperty(PROP_SAKAI_SENDSTATE, STATE_SEND_FAILED);
                                        if (adminSession.hasPendingChanges()) {
                                            adminSession.save();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (RepositoryException e) {
                LOGGER.error("sendQueuedNoticeJob Failed", e);
            }
            finally {
                if (adminSession != null)
                    adminSession.logout();
            }
        }

        /**
         * find all the notices for this advisor that are of sakai:type=notice
         * and sakai:messagebox=queue
         * 
         * @param advisorId
         * @param adminSession
         * @return
         * @throws RepositoryException
         */
        private QueryResult findQueuedNotices(String advisorId, Session adminSession) throws RepositoryException {
            String messageStorePath = ISO9075.encodePath(messagingService.getFullPathToStore(advisorId, adminSession));
            // /jcr:root/_user/_x0032_/_x0032_0/_x0032_040/message//*[@sling:resourceType="sakai/message"
            // and @sakai:type="notice and @sakai:messagebox="sakai:queue
            StringBuilder queryString = new StringBuilder("/jcr:root").append(messageStorePath).append("//*[@sling:resourceType=\"sakai/message\" and @")
                    .append(PROP_SAKAI_TYPE).append("=\"").append(TYPE_NOTICE).append("\" and @").append(PROP_SAKAI_MESSAGEBOX).append("=\"").append(BOX_QUEUE)
                    .append("\" and @").append(PROP_SAKAI_SENDSTATE).append("=\"").append(STATE_PENDING).append("\"]");
            LOGGER.info("findQueuedNotices() Using QUery {} ", queryString.toString());
            // find all the notices in the queue for this advisor
            QueryManager queryManager = adminSession.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(queryString.toString(), "xpath");
            QueryResult result = query.execute();
            return result;
        }

        /**
         * if the time when job is run is earlier or equal to the
         * sakai:sendDate, then it is time(or past time) to send the notice
         * 
         * @param notice
         * @return
         * @throws RepositoryException
         */
        private boolean timeToSend(Node notice) throws RepositoryException {
            boolean timeToSend = false;
            try {
                Property sendDateProp = notice.getProperty(MyBerkeleyMessageConstants.PROP_SAKAI_SENDDATE);
                Calendar sendDate = sendDateProp != null ? sendDateProp.getDate() : null;
                if (sendDate != null) {
                    Calendar now = Calendar.getInstance();
                    if (now.compareTo(sendDate) >= 0) {
                        LOGGER.debug("timeToSend() sendDate: {} is earlier than or same as now: {}, sending notice: {}", new Object[] { sendDate.getTime(), now.getTime(),
                                notice.getPath() });
                        timeToSend = true;
                    }
                    else {
                        LOGGER.debug("timeToSend() sendDate: {} is later than now: {}, NOT sending notice: {}", new Object[] { sendDate.getTime(), now.getTime(),
                                notice.getPath() });
                    }
                }
                else {
                    String msg = "timeToSend() sendDate is null, cannot send notice: " + notice.getPath();                    
                    throw new RepositoryException(msg);
                }
            }
            catch (PathNotFoundException e) {
                LOGGER.error("timeToSend() sendDate not found, cannot send notice: " + notice.getPath(), e);
                throw e;
            }
            return timeToSend;
        }

        /**
         * send the notice using the method employed by the MessagePostProcessor
         * so as to do things the messaging bundle way, the actual notice node
         * copying will be done by the NoticeHandler
         * 
         * @param noticeNode
         * @param advisorId
         * @throws RepositoryException
         */
        private void sendNotice(Node noticeNode, String advisorId) throws RepositoryException {
            Property sendStateProp = noticeNode.getProperty(PROP_SAKAI_SENDSTATE);
            String state = sendStateProp.getValue().getString();
            if (STATE_NONE.equals(state) || STATE_PENDING.equals(state)) {
                noticeNode.setProperty(PROP_SAKAI_SENDSTATE, STATE_NOTIFIED);
                Dictionary<String, Object> messageDict = new Hashtable<String, Object>();
                messageDict.put(EVENT_LOCATION, noticeNode.getPath());
                messageDict.put("user", advisorId);
                LOGGER.info("Sending queued notice by sending admin Event for notice: " + noticeNode.getPath());
                Event pendingMessageEvent = new Event(PENDINGMESSAGE_EVENT, messageDict);
                // KERN-790: Initiate a synchronous event.
                eventAdmin.sendEvent(pendingMessageEvent);
            }
        }
    }
}
