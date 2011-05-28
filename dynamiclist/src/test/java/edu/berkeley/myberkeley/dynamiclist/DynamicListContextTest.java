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
        "/colleges/ENV DSGN/standings/undergrad/majors/LIMITED",
        "/colleges/ENV DSGN/standings/undergrad/majors/URBAN STUDIES",
        "/colleges/ENV DSGN/standings/grad",
        "/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE",
        "/colleges/ENV DSGN/standings/grad/programs/CITY PLAN",
        "/colleges/ENV DSGN/departments/*"
    ), ImmutableSet.of(
        "/student/public/*",
        "/student/level/Junior",
        "/student/degreeProgram/M.S.",
        "/student/degreeProgram/PH.D."
    ));
    dynamicListService = new DynamicListSparseSolrImpl();
  }

  @Test
  public void testJsonParsing() throws Exception {
     String solrQuery = dynamicListService.getSolrQueryForCriteria(context,
        "/colleges/ENV DSGN/standings/grad");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/grad\"", solrQuery);
    solrQuery = dynamicListService.getSolrQueryForCriteria(context,
        "{OR: [" +
          "{AND: [\"/colleges/ENV DSGN/standings/grad\", " +
                "{OR: [\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\", " +
                       "\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\"] }" +
          "]}," +
          "\"/colleges/ENV DSGN/standings/undergrad/majors/LIMITED\", " +
          "\"/colleges/ENV DSGN/standings/undergrad/majors/URBAN STUDIES\"" +
        "]}");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "((myb-demographics:\"/colleges/ENV DSGN/standings/grad\" AND " +
        "(myb-demographics:\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\" OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\")) OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/undergrad/majors/LIMITED\" OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/undergrad/majors/URBAN STUDIES\")", solrQuery);
  }

  @Test
  public void testAlternativeSyntax() throws Exception {
     String solrQuery = dynamicListService.getSolrQueryForCriteria(context,
        "{ANY: [" +
          "{ALL: [\"/colleges/ENV DSGN/standings/grad\", " +
                "{ANY: [\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\", " +
                       "\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\"] }" +
          "]}," +
          "\"/colleges/ENV DSGN/standings/undergrad/majors/LIMITED\", " +
          "\"/colleges/ENV DSGN/standings/undergrad/majors/URBAN STUDIES\"" +
        "]}");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "((myb-demographics:\"/colleges/ENV DSGN/standings/grad\" AND " +
        "(myb-demographics:\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\" OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\")) OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/undergrad/majors/LIMITED\" OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/undergrad/majors/URBAN STUDIES\")", solrQuery);
  }

  @Test(expected = AccessControlException.class)
  public void testBadCriteria() throws AccessControlException, JSONException {
    dynamicListService.getSolrQueryForCriteria(context,
        "{OR: [\"/colleges/ENV DSGN/standings/grad\", \"NOT-ALLOWED\"]}");
  }

  @Test(expected = AccessControlException.class)
  public void testNoCriteria() throws AccessControlException, JSONException {
    dynamicListService.getSolrQueryForCriteria(context,
        "");
  }

  @Test
  public void testWildcardAllowed() throws AccessControlException, JSONException {
    String solrQuery = dynamicListService.getSolrQueryForCriteria(context,
        "/colleges/ENV DSGN/departments/POSTPOSTSTRUCTURALISMO");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "myb-demographics:\"/colleges/ENV DSGN/departments/POSTPOSTSTRUCTURALISMO\"", solrQuery);
  }

  @Test
  public void testFilterParsing() throws Exception {
     String solrQuery = dynamicListService.getSolrQueryForCriteria(context,
         "{ANY: [" +
           "{" +
             "ANY: [ \"/colleges/ENV DSGN/standings/undergrad/majors/LIMITED\"," +
                     "\"/colleges/ENV DSGN/standings/undergrad/majors/URBAN STUDIES\"]," +
             "FILTER: \"/student/level/Junior\"" +
           "}, {" +
             "ALL: \"/colleges/ENV DSGN/standings/grad\", " +
             "FILTER: {ANY: [ \"/student/degreeProgram/M.S.\", \"/student/degreeProgram/PH.D.\" ] }" +
           "}" +
         "]}");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "(((myb-demographics:\"/colleges/ENV DSGN/standings/undergrad/majors/LIMITED\" OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/undergrad/majors/URBAN STUDIES\") AND " +
        "myb-demographics:\"/student/level/Junior\") OR " +
        "(myb-demographics:\"/colleges/ENV DSGN/standings/grad\" AND " +
        "(myb-demographics:\"/student/degreeProgram/M.S.\" OR "+
        "myb-demographics:\"/student/degreeProgram/PH.D.\")))",
        solrQuery);
  }

  @Test(expected = AccessControlException.class)
  public void testFilterAsCriteria() throws AccessControlException, JSONException {
    dynamicListService.getSolrQueryForCriteria(context,
        "{OR: [" +
          "{OR: [\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\", " +
                "\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\"] }" +
          "\"/student/level/Junior\"" +
        "]}");
  }

  @Test(expected = AccessControlException.class)
  public void testFilterWithoutCriteria() throws AccessControlException, JSONException {
    dynamicListService.getSolrQueryForCriteria(context,
        "{FILTER: [\"/student/level/Junior\"]}");
  }

  @Test(expected = AccessControlException.class)
  public void testBadFilter() throws AccessControlException, JSONException {
    dynamicListService.getSolrQueryForCriteria(context,
        "{OR: [" +
          "{OR: [\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\", " +
                "\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\"] }" +
        "], FILTER: \"/student/top/secret/stuff\"}");
  }

  @Test
  public void testWildcardFilterAllowed() throws AccessControlException, JSONException {
    String solrQuery = dynamicListService.getSolrQueryForCriteria(context,
        "{ANY: [\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\", " +
                "\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\"]," +
         "FILTER: \"/student/public/attribute\"" +
        "}");
    assertEquals("resourceType:myberkeley/personalDemographic AND " +
        "((myb-demographics:\"/colleges/ENV DSGN/standings/grad/programs/ARCHITECTURE\" OR " +
        "myb-demographics:\"/colleges/ENV DSGN/standings/grad/programs/CITY PLAN\") AND " +
        "myb-demographics:\"/student/public/attribute\")", solrQuery);
  }
}
