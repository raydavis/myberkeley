Module to enable sending a custom message type, notice, using the existing nakamura.messaging module to the greatest extend practicable.

Currently, the is a CreateNoticeServlet roughed in for possible future use in adding necessary additional functionality but it is not in use at present.

There is a custom MessagePreProcessing service, the CreateNoticePreProcessor.java, MessageTransport service, the NoticeHandler.java, and a custom Route service,
the NoticeRoute.java.

All these services register with the nakamura.messaging bundle and will be called for any message with a sakai:type=notice attribute from a URL that the CreateMessageServlet
handles.

Here's an example curl POST that sends a notice type message from John King to Ray Davis:
curl -u 271592:testuser -F sakai:type=notice -F sakai:sendstate=pending -F sakai:messagebox=outbox  -F sakai:to=internal:211159 -F sakai:from=271592 \
-F sakai:subject="Testing Notices" -F sakai:body="Sending my first Notice" -F sakai:category=message http://localhost:8080/_user/2/27/271592/message.create.html

See also the README for nakamura.messaging

My current understanding of the messaging workflow:

1) client POSTs to message.create.html with message attributes.  This is handled by CreateMessageServlet.
2) the servlet calls the PreProcessor for the sakai:type.  This preprocessor has registered itself with the Servlet as a service.  It can check Authz for
the user and/or validate message attributes.
3) the servlet extracts the POST properties and hands off the to the MessageServiceImpl getting the message node back for writing to the HttpResponse.
4) the MessageService creates the message and persists it as a node under the senders _user node, for example, _user/2/27/271592/message/...../${uid}.
5) the CreateMessageServlet forwards through to the SlingPostServlet
6) the MessagePostProcessor has registered itself with the SlingPostServlet as a Service and gets called just before the response is sent.
7) if the MessagePostProcessor finds a changed message node with a messagebox=outbox attribute and a sendstate=none or a sendstate=pending attribute, it generates
a pendingMessage osgi.service.Event and sends it synchronously to the osgi EventAdmin.
8) MessageSentLister service has registered to listen for @Property(name = "event.topics", value = "org/sakaiproject/nakamura/message/pending")
8) the MessageTransport services have registered with the MessageSentLister
9) the MessageRouterManager service has registered with the MessageSentListener
10) the MessageRouter services have registered with the MessageRouterManager
11) the MessageSentLister service handles the Event and if the Event.location is a sakai:message node, gets the MessageRoutes from the messageRouterManager. 
12) A side effect of calling messageRouterManger.getRoutes() is that every Router.route(Node n, MessageRoutes routing) method is called.  
Here is where an external email in addition to the internal message could be sent. 
13)The listener then iterates through the messageTransport list calling transport.send(routes, event, messageNode).  
14) MessageTransport aka Handler, e.g. NoticeHandler, then iterates through the routes, i.e. a list of recipient nodes, and creates a new message node under the recipient
node in a sakai:state=notified.
15) the HttpResponse containing the json from the message node creation is sent back to the client.

NOTE: there are actually two switches in play, the message type and message transport.  The type switch, e.g MessageContstants.TYPE_INTERNAL = "internal" and MyBerkeleyMessageConstants.TYPE_NOTICE = "notice" 
that is used in the Node property sakai:type.  The Transport switch, e.g. MessageTransport.INTERNAL_TRANSPORT and MyBerkeleyMessageConstants.NOTICE_TRANSPORT is used in MessageTransport aka Handler
logic.  This is also used in the sakai:to message Node property and curl command parameter e.g "sakai:to=internal:211159,internal:322279".  The first, "internal" part is used as a switch in
MessageTransport(aka Handler).send() logic.  Currently, we are using "internal" transport even for sakai:type=notice messages which is a little confusing.  If needed, we
could use use a "notice" transport as well.  NOTE ALSO that there must be NO WHITE SPACE in the sakai:to parameter for multiple recipients as the split parsing does not trim
properly. If not first part is suppliend, e.g."sakai:to=211159,322279", the Transport will default to "internal".  Make sure there is no white space in this syntax either.