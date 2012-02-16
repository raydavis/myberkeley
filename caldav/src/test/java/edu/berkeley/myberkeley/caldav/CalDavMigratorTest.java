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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import edu.berkeley.myberkeley.caldav.api.CalDavConnector;
import edu.berkeley.myberkeley.caldav.api.CalDavException;
import edu.berkeley.myberkeley.caldav.api.CalendarURI;
import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.util.LitePersonalUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class CalDavMigratorTest extends CalDavTests {
  private static final String OWNER = "mtwain";
  private static final List<String> PROPS_TO_MATCH_BY_EQUALS = ImmutableList.of(
      Property.CATEGORIES, Property.DESCRIPTION, Property.DURATION,
      Property.STATUS, Property.SUMMARY,
      Property.DTEND, Property.DTSTART, Property.DUE
  );
  // Bedework sticks its own UID on Location properties.
  private static final List<String> PROPS_TO_MATCH_BY_VALUE = ImmutableList.of(
      Property.LOCATION
  );

  private CalDavMigrator calDavMigrator;
  private CalDavConnector embeddedCalDav;
  private CalDavConnectorProviderImpl calDavConnectorProvider;
  private Session adminSession;

  @Before
  public void setup() throws Exception {
    Assume.assumeTrue(initializeCalDavSource());
    calDavConnectorProvider = new CalDavConnectorProviderImpl();
    calDavConnectorProvider.adminUsername = "admin";
    calDavConnectorProvider.adminPassword = calDavPassword;
    calDavConnectorProvider.calDavServerRoot = calDavServer;

    BaseMemoryRepository baseMemoryRepository = new BaseMemoryRepository();
    Repository repository = baseMemoryRepository.getRepository();
    adminSession = repository.loginAdministrative();
    makeMinimalUser(OWNER, adminSession);
    EmbeddedCalDavProvider embeddedCalDavProvider = new EmbeddedCalDavProvider();
    embeddedCalDavProvider.repository = repository;

    adminConnector = calDavConnectorProvider.getAdminConnector(OWNER);
    embeddedCalDav = embeddedCalDavProvider.getAdminConnector(OWNER);
    deleteAll();
    calDavMigrator = new CalDavMigrator();
    calDavMigrator.toCalDavProvider = embeddedCalDavProvider;
  }

  @After
  public void cleanup() throws CalDavException {
    if (initializeCalDavSource()) {
      deleteAll();
    }
  }
  
  @Test
  public void userNotInBedework() throws Exception {
    String unknownUserId = "CalDavMigratorTest" + (new Date()).getTime();
    makeMinimalUser(unknownUserId, adminSession);
    long count = calDavMigrator.migrateCalDav(unknownUserId, calDavConnectorProvider);
    assertEquals(0, count);
  }
  
  @Test
  public void multipleCalendarMigration() throws Exception {
    assertTrue(embeddedCalDav.getCalendarUris().isEmpty());
    assertTrue(adminConnector.getCalendarUris().isEmpty());

    // Load up external service.
    adminConnector.putCalendar(buildOverdueTask("Created by CalDavMigratorTest"));
    adminConnector.putCalendar(buildPastEvent("Created by CalDavMigratorTest"));
    adminConnector.putCalendar(buildVevent("Created by CalDavMigratorTest"));
    adminConnector.putCalendar(buildVTodo("Created by CalDavMigratorTest"));
    List<CalendarURI> originalUris = adminConnector.getCalendarUris();
    assertEquals(4, originalUris.size());
    List<CalendarWrapper> originalWrappers = adminConnector.getCalendars(originalUris);
    Map<String, CalendarWrapper> originalUidsToWrappers = Maps.newHashMapWithExpectedSize(originalWrappers.size());
    for (CalendarWrapper wrapper : originalWrappers) {
      originalUidsToWrappers.put(wrapper.getComponent().getProperty(Property.UID).getValue(), wrapper);
    }

    long count = calDavMigrator.migrateCalDav(OWNER, calDavConnectorProvider);
    assertEquals(originalUris.size(), count);

    List<CalendarURI> migratedUris = embeddedCalDav.getCalendarUris();
    assertEquals(originalUris.size(), migratedUris.size());
    List<CalendarWrapper> migratedWrappers = embeddedCalDav.getCalendars(migratedUris);
    assertEquals(originalWrappers.size(), migratedWrappers.size());
    for (CalendarWrapper migratedWrapper : migratedWrappers) {
      Component migratedCalendar = migratedWrapper.getComponent();
      PropertyList migratedProps = migratedCalendar.getProperties();
      LOGGER.info("migratedProps = " + migratedProps);
      String migratedUid = migratedProps.getProperty(Property.UID).getValue();
      CalendarWrapper originalWrapper = originalUidsToWrappers.get(migratedUid);
      assertNotNull(originalWrapper);
      for (Object originalPropObj : originalWrapper.getComponent().getProperties()) {
        Property originalProp = (Property) originalPropObj;
        String propName = originalProp.getName();
        if (PROPS_TO_MATCH_BY_EQUALS.contains(propName)) {
          LOGGER.info("originalProp = {}", originalProp);
          assertTrue(migratedProps.contains(originalProp));
        } else if (PROPS_TO_MATCH_BY_VALUE.contains(propName)) {
          LOGGER.info("originalProp = {}, value = {}", originalProp, originalProp.getValue());
          assertEquals(originalProp.getValue(), migratedProps.getProperty(propName).getValue());
        }
      }
    }
  }

  private void makeMinimalUser(String userId, Session adminSession) throws StorageClientException, AccessDeniedException {
    AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
    if (authorizableManager.findAuthorizable(userId) == null) {
      authorizableManager.createUser(userId, userId, "test", null);
      ContentManager contentManager = adminSession.getContentManager();
      String homePath = LitePersonalUtils.getHomePath(userId);
      if (!contentManager.exists(homePath)) {
        contentManager.update(new Content(homePath, ImmutableMap.of("sling:resourceType", (Object) "sakai/user-home")));
      }
    }
  }
}
