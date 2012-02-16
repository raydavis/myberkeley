/*

 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package edu.berkeley.myberkeley.dynamiclist;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.Iterables;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListContext;
import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

@Component(immediate = true)
@Service
public class DynamicListSparseSolrImpl implements DynamicListService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListSparseSolrImpl.class);

  // Bump up the number of returns per query fetch to our anticipated maximum.
  // This would normally be considered an extreme abuse of Solr / Lucene,
  // but for our specific purposes it's an OK workaround to get us started.
  // Eventually we'll likely replace this Solr implementation by a Sparse find
  // or a straightforward SQL query against the source DB.
  private static final Integer SOLR_PAGE_SIZE = 20000;

  private static final Map<String, String> QUERY_TO_CONNECTORS_MAP = ImmutableMap.of(
      "AND", "AND",
      "OR", "OR",
      "ALL", "AND",
      "ANY", "OR"
  );
  private static final String PERSONAL_DEMOGRAPHIC_STORE_NAME = "_myberkeley-demographic";
  private static final Map<String, Object> ALL_USER_HOMES_QUERY_MAP = ImmutableMap.of(
      JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
      (Object) UserConstants.USER_HOME_RESOURCE_TYPE,
      "_items", new Long(20000)
  );

  @Reference
  SolrServerService solrSearchService;

  @Reference
  SlingRepository slingRepository;

  public Collection<String> getUserIdsForCriteria(DynamicListContext context, String criteriaJson) {
    List<String> matchingIds = new ArrayList<String>();
    try {
      // Transform the generic JSON expression of critera to a Solr query string, checking
      // that all specified criteria are actually permitted by the target context.
      String solrQueryString = getSolrQueryForCriteria(context, criteriaJson);
      SolrServer solrServer = solrSearchService.getServer();
      SolrQuery solrQuery = new SolrQuery(solrQueryString);
      solrQuery.setRows(SOLR_PAGE_SIZE);
      try {
        LOGGER.info("Performing Query {} ", URLDecoder.decode(solrQuery.toString(), "UTF-8"));
      } catch (UnsupportedEncodingException ignored) {
      }
      QueryResponse response = solrServer.query(solrQuery);
      SolrDocumentList resultList = response.getResults();
      LOGGER.info("Got {} hits in {} ms", resultList.size(), response.getElapsedTime());
      if (resultList.size() >= SOLR_PAGE_SIZE) {
        LOGGER.warn("AT PAGE SIZE LIMIT!");
      }
      for (SolrDocument solrDocument : resultList) {
        LOGGER.debug("  solrDocument=" + solrDocument.getFieldValuesMap());
        String fullPath = (String) solrDocument.getFirstValue("path");
        String userId = PathUtils.getAuthorizableId(fullPath);
        matchingIds.add(userId);
      }
    } catch (SolrServerException e) {
      LOGGER.warn("Could not perform query=" + criteriaJson, e);
    } catch (AccessControlException e) {
      LOGGER.warn("Could not perform query=" + criteriaJson, e);
    } catch (JSONException e) {
      LOGGER.warn("Could not perform query=" + criteriaJson, e);
    }
    return matchingIds;
  }

  public Collection<String> getUserIdsForNode(Content list, Session session) throws StorageClientException,
          AccessDeniedException, RepositoryException {
    String criteria = (String) list.getProperty(DynamicListService.DYNAMIC_LIST_STORE_CRITERIA_PROP);
    String contextName = (String) list.getProperty(DynamicListService.DYNAMIC_LIST_STORE_CONTEXT_PROP);

    if ( criteria == null || contextName == null ) {
      LOGGER.warn("Dynamic list at " + list.getPath() + " does not have context or criteria info, cannot perform search");
      return new ArrayList<String>(0);
    }

    javax.jcr.Session jcrSession = null;

    try {
      jcrSession = this.slingRepository.loginAdministrative(null);
      Node listContextNode = jcrSession.getNode("/var/myberkeley/dynamiclists/" + contextName);
      DynamicListContext context = new DynamicListContext(listContextNode);
      return this.getUserIdsForCriteria(context, criteria);
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }
  }

  private StringBuilder appendClause(DynamicListContext context, Object jsonVal, StringBuilder sb,
      String connector, boolean isFilterClause) throws AccessControlException, JSONException {
    if (jsonVal instanceof String) {
      String match = (String) jsonVal;
      LOGGER.debug("appendClause: match='" + match + "', sb='" + sb.toString() + "', connector='" + connector + "'");
      if ((!isFilterClause && !context.isClauseAllowed(match)) ||
          (isFilterClause && !context.isFilterAllowed(match))) {
        throw new AccessControlException("Allowed criteria for " + context.getContextId() + " do not include " + match);
      }
      sb.append("myb-demographics:\"").append((String) jsonVal).append("\"");
    } else if (jsonVal instanceof JSONObject) {
      JSONObject jsonObj = (JSONObject) jsonVal;
      if ((jsonObj.length() > 2) || (jsonObj.length() < 1)) {
        throw new JSONException("Each clause must have only one connector and one filter: " + jsonObj.toString());
      }
      boolean hasFilter = false;
      String newConnector = null;
      Iterator<String> keys = jsonObj.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if ("FILTER".equals(key)) {
          if (isFilterClause) {
            throw new JSONException("Filter clauses do not have filters: " + jsonObj.toString());
          }
          hasFilter = true;
        } else {
          newConnector = key;
        }
      }
      if (hasFilter && newConnector == null) {
        throw new AccessControlException("Standalone filter specified in " + jsonObj.toString());
      }
      if (!QUERY_TO_CONNECTORS_MAP.containsKey(newConnector)) {
        throw new JSONException("Unknown query connector: " + jsonObj.toString());
      }
      if (hasFilter) {
        sb.append("(");
      }
      Object inner = jsonObj.get(newConnector);
      appendClause(context, inner, sb, QUERY_TO_CONNECTORS_MAP.get(newConnector), isFilterClause);
      if (hasFilter) {
        sb.append(" AND ");
        Object filter = jsonObj.get("FILTER");
        appendClause(context, filter, sb, null, true);
        sb.append(")");
      }
    } else if (jsonVal instanceof JSONArray) {
      JSONArray array = (JSONArray) jsonVal;
      if ((array.length() > 0) && (connector == null)) {
        throw new JSONException("No connector specified for array: " + array.toString());
      }
      sb.append("(");
      for (int i = 0; i < array.length(); i++) {
        if (i > 0) {
          sb.append(" ").append(connector).append(" ");
        }
        appendClause(context, array.get(i), sb, connector, isFilterClause);
      }
      sb.append(")");
    } else {
      throw new JSONException("Could not parse Dynamic List criteria: " + jsonVal);
    }
    return sb;
  }

  public String getSolrQueryForCriteria(DynamicListContext context, String criteriaJsonString)
          throws AccessControlException, JSONException {
    // The criteria must either be a single matching string, or a JSON Object of the
    // form "{OR: [match1, match2, ...]}" or {AND: [match1, match2, ...]}".
    Object parsedCriteria;
    try {
      parsedCriteria = new JSONObject(criteriaJsonString);
    } catch (JSONException e) {
      parsedCriteria = criteriaJsonString;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("resourceType:").append(DynamicListService.DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT);
    sb.append(" AND ");
    appendClause(context, parsedCriteria, sb, null, false);
    return sb.toString();
  }

  @Override
  public void setDemographics(Session session, String userId, Set<String> demographicSet) throws StorageClientException, AccessDeniedException {
    final String homePath = LitePersonalUtils.getHomePath(userId);
    ContentManager contentManager = session.getContentManager();
    final String storePath = StorageClientUtils.newPath(homePath, PERSONAL_DEMOGRAPHIC_STORE_NAME);
    if (!contentManager.exists(storePath)) {
      contentManager.update(new Content(storePath, ImmutableMap.of(
          JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
          (Object) DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT)));
      // Only administrative accounts can access the demographic profile directly.
      List<AclModification> modifications = new ArrayList<AclModification>();
      AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
      AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
      AccessControlManager accessControlManager = session.getAccessControlManager();
      accessControlManager.setAcl(Security.ZONE_CONTENT, storePath, modifications.toArray(new AclModification[modifications.size()]));
    }
    Content content = contentManager.get(storePath);
    final String[] demographics;
    if (demographicSet != null) {
      demographics = demographicSet.toArray(new String[demographicSet.size()]);
      content.setProperty(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP, demographics);
    } else {
      content.removeProperty(DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP);
    }
    LOGGER.info("Set {} demographics to {}", storePath, demographicSet);
    contentManager.update(content);
  }

  @Override
  public Iterable<String> getAllUserIds(Session session) throws StorageClientException, AccessDeniedException {
    ContentManager contentManager = session.getContentManager();
    Iterable<Content> userHomes = contentManager.find(ALL_USER_HOMES_QUERY_MAP);
    return Iterables.transform(userHomes, HOME_TO_AUTHORIZABLE_ID);
  }

  public static Function<Content, String> HOME_TO_AUTHORIZABLE_ID = new Function<Content, String>() {
    @Override
    public String apply(Content content) {
      return PathUtils.getAuthorizableId(content.getPath());
    }
  };

}
