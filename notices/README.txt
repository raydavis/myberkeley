Module to enable sending a custom message type, notice, using the existing nakamura.messaging module to the greatest extend practicable.

Currently, the is a CreateMessageServlet roughed in for possible future use in adding necessary additional functionality but it is not in use at present.

There is a custom MessagePreProcessing service, the CreateNoticePreProcessor.java, MessageTransport service, the NoticeHandler.java, and a custom Route service,
the NoticeRoute.java.

All these services register with the sakai.messaging bundle and will be called for any message with a sakai:type=notice attribute from a URL that the CreateMessageServlet
handles.

Here's an example curl POST that sends a notice type message from John King to Ray Davis:
curl -u 271592:testuser -F sakai:type=notice -F sakai:sendstate=pending -F sakai:messagebox=outbox  -F sakai:to=internal:211159 -F sakai:from=271592 \
-F sakai:subject="Testing Notices" -F sakai:body="Sending my first Notice" -F sakai:category=message http://localhost:8080/_user/2/27/271592/message.create.html

See also the README for sakai.messaging