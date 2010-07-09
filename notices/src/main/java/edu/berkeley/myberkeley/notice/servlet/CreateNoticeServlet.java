/**
 * Copyright (c) 2010 The Regents of the University of California
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.berkeley.myberkeley.notice.servlet;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.sakaiproject.nakamura.api.message.CreateMessagePreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SlingServlet(resourceTypes = { "/myberkeley/notice" }, selectors = { "create" }, methods = { "POST" }, generateComponent = true, generateService = true)
@Properties(value = { @Property(name = "service.vendor", value = "University of California, Berkeley"),
        @Property(name = "service.description", value = "Endpoint to create a notice") })
@Reference(name = "createMessagePreProcessor", referenceInterface = CreateMessagePreProcessor.class, cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE, policy = ReferencePolicy.DYNAMIC)
public class CreateNoticeServlet extends SlingAllMethodsServlet {
    
    private static final long serialVersionUID = -6925414746881506489L;

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateNoticeServlet.class);

    protected Map<String, CreateMessagePreProcessor> processors = new ConcurrentHashMap<String, CreateMessagePreProcessor>();
    
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        LOGGER.info("CreateNoticeServlet.doPost(), calling superclass method");
        super.doPost(request, response);
    }
    
//    protected void doPostNew(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
//        // This is the message store resource.
//        Resource baseResource = request.getResource();
//        Session session = request.getResourceResolver().adaptTo(Session.class);
//
//        // Current user.
//        String user = request.getRemoteUser();
//
//        // Anonymous users are not allowed to post anything.
//        if (user == null || UserConstants.ANON_USERID.equals(request.getRemoteUser())) {
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Anonymous users can't send messages.");
//            return;
//        }
//
//        // This is the only check we always do, because to much code handling
//        // depends on it.
//        if (request.getRequestParameter(MessageConstants.PROP_SAKAI_TYPE) == null) {
//            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No type for this message specified.");
//            return;
//        }
//
//        // Get the sakai:type and depending on this type we call a preprocessor.
//        // If no preprocessor is found we use the internal one.
//        String type = request.getRequestParameter(MessageConstants.PROP_SAKAI_TYPE).getString();
//        CreateMessagePreProcessor preProcessor = null;
//        preProcessor = processors.get(type);
//        if (preProcessor == null) {
//            preProcessor = new InternalCreateMessagePreProcessor();
//        }
//
//        LOGGER.info("Using preprocessor: {}", preProcessor.getType());
//
//        // Check if the request is properly formed for this sakai:type.
//        try {
//            preProcessor.checkRequest(request);
//        }
//        catch (MessagingException ex) {
//            response.sendError(ex.getCode(), ex.getMessage());
//            return;
//        }
//
//        RequestParameterMap mapRequest = request.getRequestParameterMap();
//        Map<String, Object> mapProperties = new HashMap<String, Object>();
//
//        for (Entry<String, RequestParameter[]> e : mapRequest.entrySet()) {
//            RequestParameter[] parameter = e.getValue();
//            if (parameter.length == 1) {
//                mapProperties.put(e.getKey(), parameter[0].getString());
//            }
//            else {
//                String[] arr = new String[parameter.length];
//                for (int i = 0; i < parameter.length; i++) {
//                    arr[i] = parameter[i].getString();
//                }
//                mapProperties.put(e.getKey(), arr);
//            }
//        }
//        mapProperties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, MessageConstants.SAKAI_MESSAGE_RT);
//        mapProperties.put(MessageConstants.PROP_SAKAI_READ, true);
//        mapProperties.put(MessageConstants.PROP_SAKAI_FROM, user);
//
//        // Create the message.
//        Node msg = null;
//        String path = null;
//        String messageId = null;
//        try {
//            msg = messagingService.create(session, mapProperties);
//            if (msg == null) {
//                throw new MessagingException("Unable to create the message.");
//            }
//            path = msg.getPath();
//            messageId = msg.getProperty(MessageConstants.PROP_SAKAI_ID).getString();
//
//            LOGGER.info("Got message node as " + msg);
//        }
//        catch (MessagingException e) {
//            LOGGER.warn("MessagingException: " + e.getMessage());
//            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
//            return;
//        }
//        catch (RepositoryException e) {
//            LOGGER.warn("RepositoryException: " + e.getMessage());
//            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
//            return;
//        }
//
//        baseResource.getResourceMetadata().setResolutionPath("/");
//        baseResource.getResourceMetadata().setResolutionPathInfo(path);
//
//        final String finalPath = path;
//        final ResourceMetadata rm = baseResource.getResourceMetadata();
//
//        // Wrap the request so it points to the message we just created.
//        ResourceWrapper wrapper = new ResourceWrapper(request.getResource()) {
//            /**
//             * {@inheritDoc}
//             * 
//             * @see org.apache.sling.api.resource.ResourceWrapper#getPath()
//             */
//            @Override
//            public String getPath() {
//                return finalPath;
//            }
//
//            /**
//             * {@inheritDoc}
//             * 
//             * @see org.apache.sling.api.resource.ResourceWrapper#getResourceType()
//             */
//            @Override
//            public String getResourceType() {
//                return "sling/servlet/default";
//            }
//
//            /**
//             * {@inheritDoc}
//             * 
//             * @see org.apache.sling.api.resource.ResourceWrapper#getResourceMetadata()
//             */
//            @Override
//            public ResourceMetadata getResourceMetadata() {
//                return rm;
//            }
//
//        };
//
//        RequestDispatcherOptions options = new RequestDispatcherOptions();
//        SlingHttpServletResponseWrapper wrappedResponse = new SlingHttpServletResponseWrapper(response) {
//            ServletOutputStream servletOutputStream = new ServletOutputStream() {
//
//                @Override
//                public void write(int b) throws IOException {
//                }
//            };
//            PrintWriter pw = new PrintWriter(servletOutputStream);
//
//            /**
//             * {@inheritDoc}
//             * 
//             * @see javax.servlet.ServletResponseWrapper#flushBuffer()
//             */
//            @Override
//            public void flushBuffer() throws IOException {
//            }
//
//            /**
//             * {@inheritDoc}
//             * 
//             * @see javax.servlet.ServletResponseWrapper#getOutputStream()
//             */
//            @Override
//            public ServletOutputStream getOutputStream() throws IOException {
//                return servletOutputStream;
//            }
//
//            /**
//             * {@inheritDoc}
//             * 
//             * @see javax.servlet.ServletResponseWrapper#getWriter()
//             */
//            @Override
//            public PrintWriter getWriter() throws IOException {
//                return pw;
//            }
//        };
//        options.setReplaceSelectors("");
//        LOGGER.info("Sending the request out again.");
//        request.getRequestDispatcher(wrapper, options).forward(request, wrappedResponse);
//        response.reset();
//        try {
//            Node messageNode = (Node) session.getItem(path);
//
//            response.setContentType("application/json");
//            response.setCharacterEncoding("UTF-8");
//
//            JSONWriter write = new JSONWriter(response.getWriter());
//            write.object();
//            write.key("id");
//            write.value(messageId);
//            write.key("message");
//            ExtendedJSONWriter.writeNodeToWriter(write, messageNode);
//            write.endObject();
//        }
//        catch (JSONException e) {
//            throw new ServletException(e.getMessage(), e);
//        }
//        catch (RepositoryException e) {
//            throw new ServletException(e.getMessage(), e);
//        }
//    }

    protected void bindCreateMessagePreProcessor(CreateMessagePreProcessor preProcessor) {
        processors.put(preProcessor.getType(), preProcessor);
    }

    protected void unbindCreateMessagePreProcessor(CreateMessagePreProcessor preProcessor) {
        processors.remove(preProcessor.getType());
    }

    // protected void doPostOld(SlingHttpServletRequest request,
    // SlingHttpServletResponse response) throws ServletException, IOException {
    // Resource resource = request.getResource();
    // try {
    // JSONObject jsonObject = new JsonJcrNode(resource.adaptTo(Node.class));
    // jsonObject.put("thisisfrom", "noticesservlet");
    // jsonObject.write(response.getWriter());
    // }
    // catch (JSONException e) {
    // LOGGER.error(e.getMessage(), e);
    // response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
    // e.getMessage());
    // }
    // catch (RepositoryException e) {
    // LOGGER.error(e.getMessage(), e);
    // response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
    // e.getMessage());
    // }
    // }
}
