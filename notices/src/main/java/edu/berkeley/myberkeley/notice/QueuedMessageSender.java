package edu.berkeley.myberkeley.notice;

import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.BOX_ARCHIVE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.BOX_QUEUE;
import static edu.berkeley.myberkeley.api.notice.MyBerkeleyMessageConstants.TYPE_NOTICE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.BOX_OUTBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.EVENT_LOCATION;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PENDINGMESSAGE_EVENT;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_MESSAGEBOX;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_SENDSTATE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_TYPE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.PROP_SAKAI_SENDSTATE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.STATE_NONE;
import static org.sakaiproject.nakamura.api.message.MessageConstants.STATE_NOTIFIED;
import static org.sakaiproject.nakamura.api.message.MessageConstants.STATE_PENDING;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.commons.scheduler.Job;
import org.apache.sling.commons.scheduler.JobContext;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.message.MessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(label = "QueuedMessageSender", description = "Sends queued messages when they reach their send date", immediate = true)
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

    protected void activate(ComponentContext componentContext) throws Exception {
//        executes the job every minute
        String schedulingExpression = "0 * * * * ?";
        boolean canRunConcurrently = false;
        final Job sendQueuedNoticeJob = new SendQueuedNoticesJob();

        try {
            this.scheduler.addJob("sendQueuedNoticesJob", sendQueuedNoticeJob, null, schedulingExpression, canRunConcurrently);
        }
        catch (Exception e) {
            LOGGER.error("sendQueuedNoticesJob Failed", e);
        }
    }

    private class SendQueuedNoticesJob implements Job {
        public void execute(JobContext context) {
            LOGGER.info("Executing SentNoticeJob");
            try {
                Session adminSession = repository.loginAdministrative(null);

                Node advisorsGroup = adminSession.getNode("/group/g-ced-advisors");
                Node advisorNode;
                Property advisorIdProp;
                String advisorId;
                NodeIterator iter = advisorsGroup.getNodes();
                while (iter.hasNext()) {
                    advisorNode = (Node) iter.next();            
                    advisorIdProp = advisorNode.getProperty("rep:userId");
                    advisorId = advisorIdProp.getValue().getString();
                    QueryResult queuedNoticesQR = findQueuedNotices(advisorId, adminSession);
                    NodeIterator noticeIter = queuedNoticesQR.getNodes();
                    Node notice;
                    while (noticeIter.hasNext()) {
                        notice = noticeIter.nextNode();
                        notice.setProperty(PROP_SAKAI_MESSAGEBOX, BOX_OUTBOX);
                        sendNotice(notice, advisorId);
                        notice.setProperty(PROP_SAKAI_MESSAGEBOX, BOX_ARCHIVE);
                    }
                }
                adminSession.logout();
            }
            catch (RepositoryException e) {
                LOGGER.error("sendQueuedNoticeJob Failed", e);
            }
        }


        private QueryResult findQueuedNotices(String advisorId, Session adminSession) throws RepositoryException {
            String messageStorePath = ISO9075.encodePath(messagingService.getFullPathToStore(advisorId, adminSession));
            // String messageStorePath = node.getPath();
            StringBuilder queryString = new StringBuilder("/jcr:root")
                .append(messageStorePath)
                .append("//*[@sling:resourceType=\"sakai/message\" and @")
                .append(PROP_SAKAI_TYPE)
                .append("=\"")
                .append(TYPE_NOTICE)
                .append(" and @")
                .append(PROP_SAKAI_MESSAGEBOX)
                .append("=\"")
                .append(BOX_QUEUE);              
            LOGGER.info("Using QUery {} ",queryString.toString());
            // find all the notices in the queue for this advisor
            QueryManager queryManager = adminSession.getWorkspace()
                .getQueryManager();
            Query query = queryManager.createQuery(queryString.toString(), "xpath");
            QueryResult result = query.execute();
            return result;
        }
        
        private void sendNotice(Node noticeNode, String advisorId) throws RepositoryException {
            Property sendStateProp = noticeNode.getProperty(PROP_SAKAI_SENDSTATE);
            String state = sendStateProp.getValue().getString();
            if (STATE_NONE.equals(state) || STATE_PENDING.equals(state)) {
                noticeNode.setProperty(PROP_SAKAI_SENDSTATE, STATE_NOTIFIED);
                Dictionary<String, Object> messageDict = new Hashtable<String, Object>();
                messageDict.put(EVENT_LOCATION, noticeNode.getPath());
                messageDict.put("user", advisorId);
                LOGGER.info("Launched queued notice sending event for node: " + noticeNode.getPath());
                Event pendingMessageEvent = new Event(PENDINGMESSAGE_EVENT, messageDict);
                // KERN-790: Initiate a synchronous event.
                eventAdmin.sendEvent(pendingMessageEvent);
            }
        }
    }
    

    public void bindScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void unbindScheduler() {
        this.scheduler = null;
    }
}
