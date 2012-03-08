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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.resource.JcrResourceConstants;
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
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmbeddedCalDav implements CalDavConnector {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedCalDav.class);
  public static final String RESOURCETYPE = "myberkeley/calcomponent";
  public static final String STORE_NAME = "_myberkeley_calstore";
  public static final String STORE_RESOURCETYPE = "myberkeley/calstore";

  public enum JSON_PROPERTIES {
    component,
    etag,
    calendarWrapper
  }

  protected final String userId;
  protected final Session session;
  protected final String storePath;
  protected final String storeResourcePath;

  public EmbeddedCalDav(String userId, Session session) {
    this.userId = userId;
    this.session = session;
    this.storePath = StorageClientUtils.newPath(LitePersonalUtils.getHomePath(userId), STORE_NAME);
    this.storeResourcePath = StorageClientUtils.newPath(LitePersonalUtils.getHomeResourcePath(userId), STORE_NAME);
  }

  @Override
  public CalendarURI putCalendar(Calendar calendar) throws CalDavException, IOException {
    return modifyCalendar(null, calendar);
  }

  @Override
  public CalendarURI modifyCalendar(CalendarURI uri, Calendar calendar) throws CalDavException, IOException {
    boolean isCreate = (uri == null);
    try {
      ensureCalendarStoreInternal();
      if (isCreate) {
        uri = createCalendarPath();
      }
      final ContentManager contentManager = session.getContentManager();
      final String calendarContentPath = calResourcePathToStoragePath(uri.getPath());
      Content content = contentManager.get(calendarContentPath);
      if (content == null) {
        if (isCreate) {
          content = new Content(calendarContentPath, null);
        } else {
          throw new IOException("Existing calendar not found at " + uri);
        }
      } else if (isCreate) {
        throw new IOException("Existing calendar found at " + uri);
      }
      final CalendarWrapper calendarWrapper = new CalendarWrapper(calendar, uri, null);
      content.setProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, RESOURCETYPE);
      JSONObject calendarJson = calendarWrapper.toJSON();
      content.setProperty(JSON_PROPERTIES.calendarWrapper.toString(), calendarJson.toString());
      LOGGER.info("Writing calendar at {}", calendarContentPath);
      contentManager.update(content);
    } catch (StorageClientException e) {
      throw new IOException(e);
    } catch (AccessDeniedException e) {
      throw new IOException(e);
    } catch (JSONException e) {
      throw new CalDavException(e.getMessage(), e);
    }
    return uri;
  }
  
  private CalendarURI createCalendarPath() throws CalDavException {
    final CalendarURI uri;
    final String calendarUUID = UUID.randomUUID().toString();
    final String calendarResourcePath = StorageClientUtils.newPath(storeResourcePath, calendarUUID + ".ics");
    try {
      uri = new CalendarURI(
          new URI(calendarResourcePath, false), new DateTime());
    } catch (URIException uie) {
      throw new CalDavException("Unexpected URIException", uie);
    }
    return uri;
  }

  @Override
  public List<CalendarWrapper> getCalendars(List<CalendarURI> uris) throws CalDavException, IOException {
    List<CalendarWrapper> calendarWrappers = Lists.newArrayListWithExpectedSize(uris.size());
    try {
      ContentManager contentManager = session.getContentManager();
      for (CalendarURI uri : uris) {
        final String contentPath = calResourcePathToStoragePath(uri.getPath());
        if (contentManager.exists(contentPath)) {
          final Content content = contentManager.get(contentPath);
          if (content != null) {
            final CalendarWrapper calendarWrapper = new CalendarWrapper(content);
            if (calendarWrapper != null) {
              calendarWrappers.add(calendarWrapper);
            }
          }
        }
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return calendarWrappers;
  }

  @Override
  public List<CalendarWrapper> searchByDate(CalendarSearchCriteria criteria) throws CalDavException, IOException {
    List<CalendarWrapper> calendarWrappers = Lists.newArrayList(getCalendarIterator(criteria));
    criteria.sortCalendarWrappers(calendarWrappers);
    return calendarWrappers;
  }

  @Override
  public boolean hasOverdueTasks() throws CalDavException, IOException {
    CalendarSearchCriteria criteria = new CalendarSearchCriteria();
    criteria.setStart(new DateTime(0));
    criteria.setEnd(new DateTime(new Date()));
    criteria.setType(CalendarSearchCriteria.TYPE.VTODO);
    criteria.setMode(CalendarSearchCriteria.MODE.REQUIRED);
    List<CalendarWrapper> results = searchByDate(criteria);
    for (CalendarWrapper wrapper : results) {
      if (!wrapper.isCompleted()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<CalendarURI> getCalendarUris() throws CalDavException, IOException {
    List<CalendarURI> uris = new ArrayList<CalendarURI>();
    Iterator<CalendarWrapper> calChildren = getCalendarIterator(null);
    while (calChildren.hasNext()) {
      uris.add(calChildren.next().getUri());
    }
    return uris;
  }

  @Override
  public void deleteCalendar(CalendarURI uri) throws CalDavException, IOException {
    final String contentPath = calResourcePathToStoragePath(uri.getPath());
    try {
      ContentManager contentManager = session.getContentManager();
      contentManager.delete(contentPath);
    } catch (StorageClientException e) {
      throw new IOException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  @Override
  public void ensureCalendarStore() {
    try {
      ensureCalendarStoreInternal();
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private void ensureCalendarStoreInternal() throws StorageClientException, AccessDeniedException {
    ContentManager contentManager = session.getContentManager();
    if (!contentManager.exists(storePath)) {
      LOGGER.info("Will create a new read-only notification store for user at path " + storePath);
      contentManager.update(new Content(storePath, ImmutableMap.of(
          JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
          (Object) STORE_RESOURCETYPE)));
      List<AclModification> modifications = new ArrayList<AclModification>();
      AclModification.addAcl(false, Permissions.ALL, User.ANON_USER, modifications);
      AclModification.addAcl(false, Permissions.ALL, Group.EVERYONE, modifications);
      AclModification.addAcl(true, Permissions.CAN_READ, userId, modifications);
      AccessControlManager accessControlManager = session.getAccessControlManager();
      accessControlManager.setAcl(Security.ZONE_CONTENT, storePath, modifications.toArray(new AclModification[modifications.size()]));
    }
  }

  private Iterator<CalendarWrapper> getCalendarIterator(CalendarSearchCriteria criteria) {
    try {
      ContentManager contentManager = session.getContentManager();
      if (contentManager.exists(storePath)) {
        return new EmbeddedCalFilter(contentManager.listChildren(storePath), criteria);
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return Iterators.emptyIterator();
  }

  private static final Pattern homePathPattern = Pattern.compile("~(.+)\\.ics");
  public static String calResourcePathToStoragePath(String calResourcePath) {
    Matcher matcher = homePathPattern.matcher(calResourcePath);
    matcher.find();
    return LitePersonalUtils.PATH_AUTHORIZABLE + matcher.group(1);
  }
}
