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

package edu.berkeley.myberkeley.caldav.api;

import edu.berkeley.myberkeley.caldav.CalendarSearchCriteria;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.property.Categories;

import java.io.IOException;
import java.util.List;

public interface CalDavConnector {
  Categories MYBERKELEY_REQUIRED = new Categories("MyBerkeley-Required");
  Categories MYBERKELEY_ARCHIVED = new Categories("MyBerkeley-Archived");
  Categories MYBERKELEY_READ = new Categories("MyBerkeley-Read");

  CalendarURI putCalendar(Calendar calendar) throws CalDavException, IOException;

  CalendarURI modifyCalendar(CalendarURI uri, Calendar calendar) throws CalDavException, IOException;

  List<CalendarWrapper> getCalendars(List<CalendarURI> uris) throws CalDavException, IOException;

  List<CalendarWrapper> searchByDate(CalendarSearchCriteria criteria) throws CalDavException, IOException;

  boolean hasOverdueTasks() throws CalDavException, IOException;

  List<CalendarURI> getCalendarUris() throws CalDavException, IOException;

  void deleteCalendar(CalendarURI uri) throws CalDavException, IOException;

}
