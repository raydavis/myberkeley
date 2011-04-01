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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableSet;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListContext;

import org.apache.sling.commons.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.security.AccessControlException;

public class DynamicListContextTest {
  DynamicListContext context;
  DynamicListSparseSolrImpl dynamicListService;

  @Before
  public void setup() {
    context = new DynamicListContext("testcontext", ImmutableSet.of(
        "/colleges/CED/standings/undergrad/majors/LIMITED",
        "/colleges/CED/standings/undergrad/majors/URBAN STUDIES",
        "/colleges/CED/standings/grad",
        "/colleges/CED/standings/grad/programs/ARCHITECTURE",
        "/colleges/CED/standings/grad/programs/CITY PLAN"
    ));
    dynamicListService = new DynamicListSparseSolrImpl();
  }

  @Test
  public void testJsonParsing() throws Exception {
     String solrQuery = dynamicListService.getSolrQueryForCriteria(context,
        "/colleges/CED/standings/grad");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "(myb-demographics:\"/colleges/CED/standings/grad\")", solrQuery);
    solrQuery = dynamicListService.getSolrQueryForCriteria(context,
        "{OR: [" +
          "{AND: [\"/colleges/CED/standings/grad\", " +
                "{OR: [\"/colleges/CED/standings/grad/programs/ARCHITECTURE\", " +
                       "\"/colleges/CED/standings/grad/programs/CITY PLAN\"] }" +
          "]}," +
          "\"/colleges/CED/standings/undergrad/majors/LIMITED\", " +
          "\"/colleges/CED/standings/undergrad/majors/URBAN STUDIES\"" +
        "]}");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "(((myb-demographics:\"/colleges/CED/standings/grad\" AND " +
        "(myb-demographics:\"/colleges/CED/standings/grad/programs/ARCHITECTURE\" OR " +
        "myb-demographics:\"/colleges/CED/standings/grad/programs/CITY PLAN\")) OR " +
        "myb-demographics:\"/colleges/CED/standings/undergrad/majors/LIMITED\" OR " +
        "myb-demographics:\"/colleges/CED/standings/undergrad/majors/URBAN STUDIES\"))", solrQuery);
  }

  @Test(expected = AccessControlException.class)
  public void testBadCriteria() throws AccessControlException, JSONException {
    dynamicListService.getSolrQueryForCriteria(context,
        "{OR: [\"/colleges/CED/standings/grad\", \"NOT-ALLOWED\"]}");
  }

  @Test(expected = AccessControlException.class)
  public void testNoCriteria() throws AccessControlException, JSONException {
    dynamicListService.getSolrQueryForCriteria(context,
        "");
  }
}
