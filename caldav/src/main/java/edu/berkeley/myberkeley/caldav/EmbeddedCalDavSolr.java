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
package edu.berkeley.myberkeley.caldav;

import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class EmbeddedCalDavSolr extends EmbeddedCalDav {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedCalDavSolr.class);
  public static final FastDateFormat SOLR_DATE_FORMAT =
      FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"));
  private static final Integer SOLR_PAGE_SIZE = 20000;

  private SolrServerService solrSearchService;
  private final String basicSolrQuery;

  public EmbeddedCalDavSolr(String userId, Session session, SolrServerService solrSearchService) {
    super(userId, session);
    this.solrSearchService = solrSearchService;
    this.basicSolrQuery = "resourceType:" + RESOURCETYPE +
        " AND path:\"a:" + userId + "/" + STORE_NAME + "\"";
  }

  @Override
  public List<CalendarWrapper> searchByDate(CalendarSearchCriteria criteria) throws CalDavException, IOException {
    List<CalendarWrapper> matches = new ArrayList<CalendarWrapper>();
    try {
      String solrQueryString = getSolrQueryForCriteria(criteria);
      SolrDocumentList resultList = findSolrDocumentList(solrQueryString, SOLR_PAGE_SIZE);
      ContentManager contentManager = session.getContentManager();
      if (resultList.size() >= SOLR_PAGE_SIZE) {
        LOGGER.warn("AT PAGE SIZE LIMIT!");
      }
      for (SolrDocument solrDocument : resultList) {
        LOGGER.debug("  solrDocument=" + solrDocument.getFieldValuesMap());
        String path = (String) solrDocument.getFirstValue("path");
        LOGGER.debug("path = {}", path);
        Content content = contentManager.get(path);
        CalendarWrapper calendarWrapper = new CalendarWrapper(content);
        matches.add(calendarWrapper);
      }
    } catch (AccessControlException e) {
      LOGGER.warn("Could not perform query=" + criteria, e);
    } catch (StorageClientException e) {
      throw new IOException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      throw new IOException(e.getMessage(), e);
    }
    return matches;
  }

  @Override
  public boolean hasOverdueTasks() throws CalDavException, IOException {
    String solrQueryString = basicSolrQuery + " AND due_tdt:[* TO NOW] AND -content:\"STATUS:COMPLETED\"";
    SolrDocumentList solrDocumentList = findSolrDocumentList(solrQueryString, 0);
    return (solrDocumentList.getNumFound() > 0);
  }
  
  private SolrDocumentList findSolrDocumentList(String queryString, Integer rows) throws IOException {
    try {
      SolrServer solrServer = solrSearchService.getServer();
      SolrQuery solrQuery = new SolrQuery(queryString);
      solrQuery.setRows(rows);
      try {
        LOGGER.debug("Performing Query {} ", URLDecoder.decode(solrQuery.toString(), "UTF-8"));
      } catch (UnsupportedEncodingException ignored) {
      }
      QueryResponse response = solrServer.query(solrQuery);
      SolrDocumentList resultList = response.getResults();
      LOGGER.info("Got {} hits in {} ms", resultList.size(), response.getElapsedTime());
      return resultList;
    } catch (SolrServerException e) {
      throw new IOException(e.getMessage(), e);
    }
  }
  
  private String getSolrQueryForCriteria(CalendarSearchCriteria criteria) {
    StringBuilder sb = new StringBuilder(basicSolrQuery);
    if (criteria != null) {
      switch (criteria.getMode()) {
        case REQUIRED:
          sb.append(" AND -content:\"CATEGORIES:MyBerkeley-Archived\"");
          sb.append(" AND content:\"CATEGORIES:MyBerkeley-Required\"");
          break;
        case UNREQUIRED:
          sb.append(" AND -content:\"CATEGORIES:MyBerkeley-Archived\"");
          sb.append(" AND -content:\"CATEGORIES:MyBerkeley-Required\"");
          break;
        case ALL_UNARCHIVED:
          sb.append(" AND -content:\"CATEGORIES:MyBerkeley-Archived\"");
          break;
        case ALL_ARCHIVED:
          sb.append(" AND content:\"CATEGORIES:MyBerkeley-Archived\"");
          break;
      }
      switch (criteria.getType()) {
        case VEVENT:
          sb.append(" AND content:\"BEGIN:VEVENT\"");
          break;
        case VTODO:
          sb.append(" AND content:\"BEGIN:VTODO\"");
          break;
      }
      if ((criteria.getStart() != null) && (criteria.getEnd() != null)) {
        DateTime start = criteria.getStart();
        start.setUtc(true);
        DateTime end = criteria.getEnd();
        end.setUtc(true);
        sb.append(" AND dtstart_tdt:[").
            append(SOLR_DATE_FORMAT.format(start)).
            append(" TO ").
            append(SOLR_DATE_FORMAT.format(end)).
            append("]");
      }
    }
    return sb.toString();
  }
}
