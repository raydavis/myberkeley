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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Uid;
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

/**
 * Sample queries:
 * <ul>
 *   <li>resourceType:myberkeley/calcomponent AND content:"BEGIN:VTODO" AND due_tdt:[* TO NOW]</li>
 *   <li>resourceType:myberkeley/calcomponent AND content:"BEGIN:VEVENT" AND dtstart_tdt:[NOW TO *]
 *       AND content:"CATEGORIES:MyBerkeley-Required" AND path:"a:211159/_myberkeley_calstore"</li>
 * </ul>
 *
 */
@Component(immediate = true)
public class EmbeddedCalDavIndexingHandler implements IndexingHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedCalDavIndexingHandler.class);

  public enum INDEXED_FIELDS {
    content,
    dtstart_tdt,
    due_tdt
  }

  @Reference(target = "(type=sparse)")
  private ResourceIndexingService resourceIndexingService;

  @Activate
  protected void activate(Map<?, ?> props) {
    resourceIndexingService.addHandler(EmbeddedCalDav.RESOURCETYPE, this);
  }
  @Deactivate
  protected void deactivate(Map<?, ?> props) {
    resourceIndexingService.removeHandler(EmbeddedCalDav.RESOURCETYPE, this);
  }

  @Override
  public Collection<SolrInputDocument> getDocuments(RepositorySession repositorySession, Event event) {
    String path = (String) event.getProperty(FIELD_PATH);
    List<SolrInputDocument> documents = Lists.newArrayList();
    if (path != null) {
      Session session = repositorySession.adaptTo(Session.class);
      ContentManager contentManager;
      try {
        contentManager = session.getContentManager();
        final Content content = contentManager.get(path);
        if (content != null) {
          documents.add(getSolrInputFromContent(content));
        }
      } catch (AccessDeniedException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (StorageClientException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    LOGGER.debug("For path {}, got documents {}", path, documents);
    return documents;
  }

  @Override
  public Collection<String> getDeleteQueries(RepositorySession repositorySession, Event event) {
    LOGGER.debug("GetDelete for {} ", event);
    String path = (String) event.getProperty("path");
    return ImmutableList.of(FIELD_ID + ":" + ClientUtils.escapeQueryChars(path));
  }

  private SolrInputDocument getSolrInputFromContent(Content content) {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField(_DOC_SOURCE_OBJECT, content);
    final CalendarWrapper calendarWrapper = EmbeddedCalFilter.toCalendarWrapper(content);
    if (calendarWrapper != null) {
      net.fortuna.ical4j.model.Component component = calendarWrapper.getComponent();
      doc.addField(INDEXED_FIELDS.content.toString(), component.toString());
      Uid uid = (Uid) component.getProperty(Property.UID);
      if (uid != null) {
        doc.addField(FIELD_ID, uid.getValue());
      }
      DtStart dtStart = (DtStart) component.getProperty(Property.DTSTART);
      if (dtStart != null) {
        doc.addField(INDEXED_FIELDS.dtstart_tdt.toString(), dtStart.getDate());
      }
      Due due = (Due) component.getProperty(Property.DUE);
      if (due != null) {
        doc.addField(INDEXED_FIELDS.due_tdt.toString(), due.getDate());
      }
    }
    return doc;
  }
}
