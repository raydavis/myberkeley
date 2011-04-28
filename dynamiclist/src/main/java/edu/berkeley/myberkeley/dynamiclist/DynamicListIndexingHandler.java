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

import static edu.berkeley.myberkeley.api.dynamiclist.DynamicListService.DYNAMIC_LIST_CONTEXT_PROP;
import static edu.berkeley.myberkeley.api.dynamiclist.DynamicListService.DYNAMIC_LIST_CONTEXT_RT;
import static edu.berkeley.myberkeley.api.dynamiclist.DynamicListService.DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP;
import static edu.berkeley.myberkeley.api.dynamiclist.DynamicListService.DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.osgi.service.event.Event;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.IndexingHandler;
import org.sakaiproject.nakamura.api.solr.RepositorySession;
import org.sakaiproject.nakamura.api.solr.ResourceIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component(immediate = true)
public class DynamicListIndexingHandler implements IndexingHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DynamicListIndexingHandler.class);
  private static final Set<String> DYNAMIC_LIST_PROPS = ImmutableSet.of(DYNAMIC_LIST_CONTEXT_PROP, DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP);

  @Reference(target = "(type=sparse)")
  private ResourceIndexingService resourceIndexingService;

  @Activate
  protected void activate(Map<?, ?> props) {
    resourceIndexingService.addHandler(DYNAMIC_LIST_CONTEXT_RT, this);
    resourceIndexingService.addHandler(DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT, this);
  }

  @Deactivate
  protected void deactivate(Map<?, ?> props) {
    resourceIndexingService.removeHandler(DYNAMIC_LIST_CONTEXT_RT, this);
    resourceIndexingService.removeHandler(DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT, this);
  }

  /**
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDocuments(org.sakaiproject.nakamura.api.solr.RepositorySession, org.osgi.service.event.Event)
   */
  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession,
      Event event) {
    String path = (String) event.getProperty(FIELD_PATH);
    List<SolrInputDocument> documents = Lists.newArrayList();
    if (path != null) {
      Session session = repositorySession.adaptTo(Session.class);
      ContentManager contentManager;
      try {
        contentManager = session.getContentManager();
        Content content = contentManager.get(path);
        if (content != null) {
          SolrInputDocument doc = new SolrInputDocument();
          for (String prop : DYNAMIC_LIST_PROPS) {
            // Lucene cannot index null values, so no need to call hasProperty.
            Object rawVal = content.getProperty(prop);
            if (rawVal != null) {
              if (rawVal instanceof Object[]) {
                for (Object val : (Object[])rawVal) {
                  if (val != null) {
                    doc.addField(prop, val);
                  }
                }
              } else {
                doc.addField(prop, rawVal);
              }
            }
          }
          doc.addField(_DOC_SOURCE_OBJECT, content);
          documents.add(doc);
        }
      } catch (StorageClientException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    return documents;
  }

  /**
   * @see org.sakaiproject.nakamura.api.solr.IndexingHandler#getDeleteQueries(org.sakaiproject.nakamura.api.solr.RepositorySession, org.osgi.service.event.Event)
   */
  public Collection<String> getDeleteQueries(RepositorySession respositorySession,
      Event event) {
    LOGGER.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty("path");
    return ImmutableList.of(FIELD_ID + ":" + ClientUtils.escapeQueryChars(path));
  }

}
