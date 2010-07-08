package edu.berkeley.myberkeley.notice;

import org.sakaiproject.nakamura.api.message.MessageRoute;

public class NoticeRoute implements MessageRoute {
    private String transport;
    private String rcpt;
    
    public NoticeRoute(String rcpt, String transport) {
        this.rcpt = rcpt;
        this.transport = transport;
      }
    
    public String getRcpt() {
        return rcpt;
    }

    public String getTransport() {
        return transport;
    }

}
