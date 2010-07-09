package edu.berkeley.myberkeley.notice;

import javax.jcr.Node;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.message.MessageRouter;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Component(inherit = true, immediate = true)

public class NoticeRouter implements MessageRouter {

    private static final Logger LOG = LoggerFactory.getLogger(NoticeRouter.class);
    
    public int getPriority() {
        // TODO Auto-generated method stub
        return 0;
    }

    public void route(Node n, MessageRoutes routing) {
        LOG.info("NoticeRouter.route()");
// here is the place to send email in addition to internal message
    }

}
