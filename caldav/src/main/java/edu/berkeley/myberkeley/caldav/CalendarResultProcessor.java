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

import edu.berkeley.myberkeley.caldav.api.CalendarWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalendarResultProcessor {

  private List<CalendarWrapper> results;

  private CalendarSearchCriteria criteria;

  public CalendarResultProcessor(List<CalendarWrapper> results, CalendarSearchCriteria criteria) {
    this.results = results;
    this.criteria = criteria;
  }

  public List<CalendarWrapper> processResults() {
    filter();
    sort();
    return this.results;
  }

  // filter in memory for now because Bedework has bugs searching on categories.
  // TODO do the searching on the Bedework side if bugs get fixed.
  private void filter() {
    List<CalendarWrapper> filteredResults = new ArrayList<CalendarWrapper>(this.results.size());
    for (CalendarWrapper wrapper : this.results) {
      switch (this.criteria.getMode()) {
        case REQUIRED:
          if (wrapper.isRequired() && !wrapper.isArchived()) {
            filteredResults.add(wrapper);
          }
          break;
        case UNREQUIRED:
          if (!wrapper.isRequired() && !wrapper.isArchived()) {
            filteredResults.add(wrapper);
          }
          break;
        case ALL_UNARCHIVED:
          if (!wrapper.isArchived()) {
            filteredResults.add(wrapper);
          }
          break;
        case ALL_ARCHIVED:
          if (wrapper.isArchived()) {
            filteredResults.add(wrapper);
          }
          break;
      }
    }
    this.results = filteredResults;
  }

  private void sort() {
    this.criteria.sortCalendarWrappers(this.results);
  }

}
