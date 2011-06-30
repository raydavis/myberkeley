package edu.berkeley.myberkeley.notifications.job;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;

@Component(label = "MyBerkeley :: ReceiptEmailer",
        description = "Sends receipts for notifications to their senders",
        immediate = true, metatype = true)
@Service(value = ReceiptEmailer.class)
public class ReceiptEmailer {

  @Reference
  EmailSender emailSender;

}
