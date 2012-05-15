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
package edu.berkeley.myberkeley.provision;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;

import org.sakaiproject.nakamura.api.accountprovider.JdbcConnectionService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class OraclePersonAttributeProviderTest {
  OraclePersonAttributeProvider oraclePersonAttributeProvider;
  @Mock
  JdbcConnectionService jdbcConnectionService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  Connection connection;
  @Mock
  PreparedStatement preparedStatement;
  @Mock
  ResultSet resultSet;
  Map<String, Object> dataRow;

  @Before
  public void setUp() throws Exception {
    when(jdbcConnectionService.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getBigDecimal(anyString())).thenAnswer(new Answer<BigDecimal>(){
      public BigDecimal answer(InvocationOnMock invocation) {
        String column = (String) invocation.getArguments()[0];
        return (BigDecimal) dataRow.get(column);
      }
    });
    when(resultSet.getString(anyString())).thenAnswer(new Answer<String>(){
      public String answer(InvocationOnMock invocation) {
        String column = (String) invocation.getArguments()[0];
        return (String) dataRow.get(column);
      }
    });
    oraclePersonAttributeProvider = new OraclePersonAttributeProvider();
    oraclePersonAttributeProvider.jdbcConnectionService = jdbcConnectionService;
  }

  @Test
  public void staffExStudent() throws Exception {
    dataRow = new ImmutableMap.Builder<String, Object>()
        .put("affiliations", "EMPLOYEE-TYPE-STAFF,STUDENT-STATUS-EXPIRED")
        .put("email_address", "staffexstudent@example.edu")
        .put("first_name", "Xavier Student")
        .put("last_name", "Staffer")
        .put("ldap_uid", (Object) (new BigDecimal(1111)))
        .build();
    when(resultSet.next()).thenReturn(true);
    Map<String, Object> userProperties = oraclePersonAttributeProvider.getPersonAttributes("1111");
    assertNotNull(userProperties);
    assertEquals("Staff", userProperties.get("role"));
    @SuppressWarnings("unchecked")
    Collection<String> demographics = (Collection<String>) userProperties.get("myb-demographics");
    assertEquals(0, demographics.size());
    doBasicMatch(dataRow, userProperties);
  }

  @Test
  public void doubleMajor() throws Exception {
    dataRow = new ImmutableMap.Builder<String, Object>()
        .put("affiliations", "STUDENT-TYPE-REGISTERED")
        .put("college_abbr", "CONCURNT")
        .put("college_abbr2", "L & S   ")
        .put("college_abbr3", "L & S   ")
        .put("college_abbr4", "L & S   ")
        .put("level_desc_s", "Senior   ")
        .put("major_name", "DOUBLE              ")
        .put("major_name2", "POLITICAL SCIENCE   ")
        .put("major_name3", "BUSINESS ADMIN      ")
        .put("major_title2", "POLITICAL SCIENCE                                             ")
        .put("major_title3", "BUSINESS ADMINISTRATION                                       ")
        .put("new_trfr_flag", "N")
        .put("ug_grad_flag", "U")
        .put("email_address", "doublemajor@example.edu")
        .put("first_name", "Major")
        .put("last_name", "Dubble")
        .put("ldap_uid", (Object) (new BigDecimal(22222)))
        .build();
    when(resultSet.next()).thenReturn(true);
    Map<String, Object> userProperties = oraclePersonAttributeProvider.getPersonAttributes("22222");
    assertNotNull(userProperties);
    assertEquals("Undergraduate Student", userProperties.get("role"));
    assertEquals("DOUBLE : POLITICAL SCIENCE ; BUSINESS ADMINISTRATION", userProperties.get("major"));
    assertEquals("CONCURNT", userProperties.get("college"));
    List<String> expectedDemographics = Arrays.asList(new String[] {
        "/student/educ_level/Senior",
        "/student/new_trfr_flag/N",
        "/colleges/CONCURNT/standings/undergrad",
        "/colleges/CONCURNT/standings/undergrad/majors/DOUBLE",
        "/colleges/L & S/standings/undergrad",
        "/colleges/L & S/standings/undergrad/majors/POLITICAL SCIENCE",
        "/colleges/L & S/standings/undergrad/majors/BUSINESS ADMIN"
    });
    doBasicMatch(dataRow, userProperties);
    @SuppressWarnings("unchecked")
    Collection<String> demographics = (Collection<String>) userProperties.get("myb-demographics");
    assertEquals(expectedDemographics.size(), demographics.size());
    assertTrue(expectedDemographics.containsAll(demographics));
  }

  public static void doBasicMatch(Map<String, Object> data, Map<String, Object> userProperties) {
    assertEquals(data.get("ldap_uid").toString(), userProperties.get(":name"));
    assertEquals(data.get("email_address"), userProperties.get("email"));
    assertEquals(data.get("first_name"), userProperties.get("firstName"));
    assertEquals(data.get("last_name"), userProperties.get("lastName"));
  }
}
