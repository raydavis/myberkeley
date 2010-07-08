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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.jcr.JsonJcrNode;
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

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        Resource resource = request.getResource();
        try {
            JSONObject jsonObject = new JsonJcrNode(resource.adaptTo(Node.class));
            jsonObject.put("thisisfrom", "noticesservlet");
            jsonObject.write(response.getWriter());
        }
        catch (JSONException e) {
            LOGGER.error(e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
        catch (RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
