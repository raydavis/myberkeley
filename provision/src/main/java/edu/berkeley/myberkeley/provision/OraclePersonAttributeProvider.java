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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.sakaiproject.nakamura.api.accountprovider.JdbcConnectionService;
import org.sakaiproject.nakamura.api.accountprovider.PersonAttributeProvider;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component(label = "CalCentral :: Oracle Person Attribute Provider", description = "Provide CalCentral user attributes from Oracle connection")
@Service
public class OraclePersonAttributeProvider implements PersonAttributeProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(OraclePersonAttributeProvider.class);
  public static final String SELECT_PERSON_SQL =
    "select pi.LDAP_UID, pi.UG_GRAD_FLAG, pi.FIRST_NAME, pi.LAST_NAME, pi.EMAIL_ADDRESS, pi.AFFILIATIONS, " +
      "sm.MAJOR_NAME, sm.MAJOR_TITLE, sm.COLLEGE_ABBR, sm.MAJOR_NAME2, sm.MAJOR_TITLE2, sm.COLLEGE_ABBR2, " +
      "sm.MAJOR_NAME3, sm.MAJOR_TITLE3, sm.COLLEGE_ABBR3, sm.MAJOR_NAME4, sm.MAJOR_TITLE4, sm.COLLEGE_ABBR4, " +
      "sp.LEVEL_DESC_S, st.NEW_TRFR_FLAG " +
      "from BSPACE_PERSON_INFO_VW pi " +
      "left join BSPACE_STUDENT_MAJOR_VW sm on pi.LDAP_UID = sm.LDAP_UID " +
      "left join BSPACE_STUDENT_PORTAL_VW sp on pi.LDAP_UID = sp.LDAP_UID " +
      "left join BSPACE_STUDENT_TERM_VW st on pi.LDAP_UID = st.LDAP_UID " +
      "where pi.LDAP_UID = ?";
  public static final Map<String, String> UG_GRAD_FLAG_MAP = ImmutableMap.of(
      "U", "Undergraduate Student",
      "G", "Graduate Student"
  );
  public static final Map<String, String> COLLEGE_ABBR_TO_PROFILE = ImmutableMap.of(
      "ENV DSGN", "College of Environmental Design",
      "NAT RES", "College of Natural Resources"
  );

  @Reference
  JdbcConnectionService jdbcConnectionService;

  @Override
  public Map<String, Object> getPersonAttributes(String personId) {
    long ldapUid;
    try {
      ldapUid = Long.parseLong(personId);
    } catch (NumberFormatException e) {
      LOGGER.warn("Person ID {} is not numeric", personId);
      return null;
    }
    Map<String, Object> personAttributes;
    Connection connection = null;
    PreparedStatement preparedStatement = null;
    try {
      connection = jdbcConnectionService.getConnection();
      preparedStatement = connection.prepareStatement(SELECT_PERSON_SQL);
      preparedStatement.setLong(1, ldapUid);
      ResultSet resultSet = preparedStatement.executeQuery();
      if (resultSet.next()) {
        personAttributes = getPersonAttributesFromResultSet(resultSet);
      } else {
        personAttributes = null;
      }
    } catch (SQLException e) {
      personAttributes = null;
      LOGGER.error(e.getMessage(), e);
    } finally {
      try {
        if (preparedStatement != null) {
          preparedStatement.close();
        }
        if (connection != null) {
          connection.close();
        }
      } catch (SQLException e) {
        LOGGER.info("Exception during connection.close", e);
      }
    }
    return personAttributes;
  }

  public static Map<String, Object> getPersonAttributesFromResultSet(ResultSet resultSet) throws SQLException {
    Map<String, Object> personAttributes = new HashMap<String, Object>();
    BigDecimal ldapUid = resultSet.getBigDecimal("ldap_uid");
    personAttributes.put(":name", ldapUid.toBigInteger().toString());
    personAttributes.put("firstName", resultSet.getString("first_name"));
    personAttributes.put("lastName", resultSet.getString("last_name"));
    personAttributes.put("email", resultSet.getString("email_address"));
    addRoleFromResultSet(resultSet, personAttributes);
    addMajorFromResultSet(resultSet, personAttributes);
    addCollegeFromResultSet(resultSet, personAttributes);
    addDemographicsFromResultSet(resultSet, personAttributes);
    return personAttributes;
  }

  public static void addDemographicsFromResultSet(ResultSet resultSet, Map<String, Object> attributes) throws SQLException {
    Set<String> demographics = new HashSet<String>();
    // Currently we can only obtain student demographics.
    if (getAffiliations(resultSet).contains("STUDENT-TYPE-REGISTERED")) {
      final String ugGradFlag = StringUtils.stripToNull(resultSet.getString("ug_grad_flag"));
      final String standing;
      if ("G".equals(ugGradFlag)) {
        standing = "/standings/grad";
      } else if ("U".equals(ugGradFlag)) {
        standing = "/standings/undergrad";
      } else {
        LOGGER.error("Registered student {} has unknown ug_grad_flag {}", attributes.get(":name"), ugGradFlag);
        attributes.put("myb-demographics", demographics);
        return;
      }
      final String level = StringUtils.stripToNull(resultSet.getString("level_desc_s"));
      if (level != null) {
        demographics.add("/student/educ_level/" + level);
      }
      final String trfr = StringUtils.stripToNull(resultSet.getString("new_trfr_flag"));
      if (trfr != null) {
        demographics.add("/student/new_trfr_flag/" + trfr);
      }
      for (int i = 1; i <= 4; i++) {
        final String suffix = (i > 1) ? String.valueOf(i) : "";
        final String majorName = StringUtils.stripToNull(resultSet.getString("major_name" + suffix));
        if (majorName != null) {
          final String college = StringUtils.stripToNull(resultSet.getString("college_abbr" + suffix));
          demographics.add("/colleges/" + college + standing);
          demographics.add("/colleges/" + college + standing + "/majors/" + majorName);
        }
      }
    }
    attributes.put("myb-demographics", demographics);
  }

  public static void addCollegeFromResultSet(ResultSet resultSet, Map<String, Object> attributes) throws SQLException {
    String college = StringUtils.stripToNull(resultSet.getString("college_abbr"));
    if (college != null) {
      String collegeDescription = COLLEGE_ABBR_TO_PROFILE.get(college);
      if (collegeDescription != null) {
        college = collegeDescription;
      }
      attributes.put("college", college);
    }
  }

  public static void addMajorFromResultSet(ResultSet resultSet, Map<String, Object> attributes) throws SQLException {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <=4 ; i++) {
      String suffix = (i > 1) ? String.valueOf(i) : "";
      String majorName = StringUtils.stripToNull(resultSet.getString("major_name" + suffix));
      if (majorName != null) {
        String majorTitle = StringUtils.stripToNull(resultSet.getString("major_title" + suffix));
        if (i == 2) {
          sb.append(" : ");
        } else if (i > 2) {
          sb.append(" ; ");
        }
        if (majorTitle != null) {
          sb.append(majorTitle);
        } else {
          sb.append(majorName);
        }
      }
    }
    if (sb.length() > 0) {
      attributes.put("major", sb.toString());
    }
  }

  public static Set<String> getAffiliations(ResultSet resultSet) throws SQLException {
    String affiliations = resultSet.getString("affiliations");
    Set<String> affiliationSet = ImmutableSet.copyOf(affiliations.split(","));
    return affiliationSet;
  }

  public static void addRoleFromResultSet(ResultSet resultSet, Map<String, Object> attributes) throws SQLException {
    final String role;
    Set<String> affiliationSet = getAffiliations(resultSet);
    if (affiliationSet.contains("STUDENT-TYPE-REGISTERED")) {
      String ugGradFlag = resultSet.getString("ug_grad_flag");
      if (ugGradFlag != null) {
        role = UG_GRAD_FLAG_MAP.get(ugGradFlag);
      } else {
        role = "Student";
      }
    } else if (affiliationSet.contains("EMPLOYEE-TYPE-ACADEMIC")) {
      role = "Instructor";
    } else if (affiliationSet.contains("EMPLOYEE-TYPE-STAFF")) {
      role = "Staff";
    } else if (affiliationSet.contains("AFFILIATE-TYPE-VISITING")) {
      role = "Instructor";
    } else {
      role = "Guest";
    }
    attributes.put("role", role);
  }

}
