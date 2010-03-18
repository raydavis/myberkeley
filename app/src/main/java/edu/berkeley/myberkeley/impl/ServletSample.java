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
package edu.berkeley.myberkeley.impl;

import java.io.IOException;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.jcr.JsonJcrNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @scr.component immediate="true" label="MyBerkeleyStarterServletSample"
 * @scr.service interface="javax.servlet.Servlet"
 * @scr.property name="service.description" value="Sample servlet triggered by selector-and-resource-type"
 * @scr.property name="sling.servlet.resourceTypes" value="myberkeley/scriptedsample"
 * @scr.property name="sling.servlet.methods" value="GET"
 * @scr.property name="sling.servlet.selectors" value="servletized"
 * @scr.property name="sling.servlet.extensions" value="json"
 */
public class ServletSample extends SlingSafeMethodsServlet {
	private static final long serialVersionUID = 4491499379513002314L;
	private static final Logger LOGGER = LoggerFactory.getLogger(ServletSample.class);

	@Override
	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		Resource resource = request.getResource();
		try {
			JSONObject jsonObject = new JsonJcrNode(resource.adaptTo(Node.class));
			jsonObject.put("thisisfrom", "servletsample");
			jsonObject.write(response.getWriter());
		} catch (JSONException e) {
			LOGGER.error(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} catch (RepositoryException e) {
			LOGGER.error(e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
		return;
	}
	
}
