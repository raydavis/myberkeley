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
import net.fortuna.ical4j.filter.Filter;
import net.fortuna.ical4j.filter.PeriodRule;
import net.fortuna.ical4j.filter.Rule;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * TODO This basic content-system approach should be replaced by a Solr search.
 */
public class EmbeddedCalFilter implements Iterator<CalendarWrapper> {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedCalFilter.class);
  private final Iterator<Content> contentIterator;
  private final CalendarSearchCriteria criteria;
  private CalendarWrapper next;

  public EmbeddedCalFilter(Iterator<Content> contentIterator, CalendarSearchCriteria criteria) {
    this.contentIterator = contentIterator;
    this.criteria = criteria;
    fetchNext();
  }

  @Override
  public boolean hasNext() {
    return (next != null);
  }

  @Override
  public CalendarWrapper next() {
    if (next == null) {
      throw new NoSuchElementException();
    }
    CalendarWrapper value = next;
    fetchNext();
    return value;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private void fetchNext() {
    next = null;
    while (contentIterator.hasNext() && (next == null)) {
      final CalendarWrapper candidate = toCalendarWrapper(contentIterator.next());
      if (isMatch(candidate, criteria)) {
        next = candidate;
      }
    }
  }

  public static CalendarWrapper toCalendarWrapper(Content content) {
    if (content.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).equals(EmbeddedCalDav.RESOURCETYPE)) {
      Map<String, Object> props = content.getProperties();
      LOGGER.debug("Content {} props = {}", content, props);
      Object calendarWrapperJson = props.get(EmbeddedCalDav.JSON_PROPERTIES.calendarWrapper.toString());
      if (calendarWrapperJson != null) {
        try {
          CalendarWrapper calendarWrapper = new CalendarWrapper(new JSONObject((String) calendarWrapperJson));
          return calendarWrapper;
        } catch (JSONException e) {
          LOGGER.error(e.getMessage(), e);
        } catch (CalDavException e) {
          LOGGER.error(e.getMessage(), e);
        }
      } else {
        LOGGER.warn("No calendarWrapper found for {}", content.getPath());
      }
    }
    return null;
  }

  public static boolean isMatch(CalendarWrapper calendarWrapper, CalendarSearchCriteria criteria) {
    if (criteria == null) {
      return true;
    }
    
    switch (criteria.getMode()) {
      case REQUIRED:
        if (calendarWrapper.isArchived() || !calendarWrapper.isRequired()) {
          return false;
        }
        break;
      case UNREQUIRED:
        if (calendarWrapper.isArchived() || calendarWrapper.isRequired()) {
          return false;
        }
        break;
      case ALL_UNARCHIVED:
        if (calendarWrapper.isArchived()) {
          return false;
        }
        break;
      case ALL_ARCHIVED:
        if (!calendarWrapper.isArchived()) {
          return false;
        }
        break;
    }

    final Component component = calendarWrapper.getComponent();
    final String componentName;
    if (component != null) {
      componentName = component.getName();
    } else {
      componentName = "";
    }
    switch (criteria.getType()) {
      case VEVENT:
        if (!componentName.equals(Component.VEVENT)) {
          return false;
        }
        break;
      case VTODO:
        if (!componentName.equals(Component.VTODO)) {
          return false;
        }
        break;
    }
    
    final Period period = new Period(new DateTime(criteria.getStart()), new DateTime(criteria.getEnd()));
    final Rule periodRule = new PeriodRule(period);
    final Filter filter = new Filter(new Rule[] {periodRule}, Filter.MATCH_ALL);
    Object[] dateMatches = filter.filter(new Object[]{component});
    return (dateMatches.length > 0);
  }
}
