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
package edu.berkeley.myberkeley.api.dynamiclist;


import org.sakaiproject.nakamura.util.JcrUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

public class DynamicListContext {
  private String contextId;
  private Set<String> allowedClauses;
  private Set<String> allowedFilters;
  private Pattern allowedClausesPattern;
  private Pattern allowedFiltersPattern;

  public DynamicListContext(String contextId, Set<String> allowedClauses, Set<String> allowedFilters) {
    this.contextId = contextId;
    this.allowedClauses = allowedClauses;
    this.allowedFilters = allowedFilters;
    this.allowedClausesPattern = stringsToPattern(allowedClauses);
    this.allowedFiltersPattern = stringsToPattern(allowedFilters);
  }

  public DynamicListContext(String contextId, Set<String> allowedClauses) {
    this(contextId, allowedClauses, null);
  }

  public DynamicListContext(Node node) throws RepositoryException {
    this(node.getProperty(DynamicListService.DYNAMIC_LIST_CONTEXT_PROP).getString(),
        propertiesToStringSet(node, DynamicListService.DYNAMIC_LIST_CONTEXT_CLAUSES_PROP),
        propertiesToStringSet(node, DynamicListService.DYNAMIC_LIST_CONTEXT_FILTERS_PROP));
  }

  public boolean isClauseAllowed(String clause) {
    return allowedClausesPattern.matcher(clause).matches();
  }

  public boolean isFilterAllowed(String filter) {
    return allowedFiltersPattern.matcher(filter).matches();
  }

  static private Set<String> propertiesToStringSet(Node node, String property) throws RepositoryException {
    Value[] vals = JcrUtils.getValues(node, property);
    Set<String> stringSet = new HashSet<String>(vals.length);
    for (Value val : vals) {
      stringSet.add(val.getString());
    }
    return stringSet;
  }

  static private Pattern stringsToPattern(Set<String> strings) {
    StringBuilder sb = new StringBuilder();
    for (String original : strings) {
      if (sb.length() > 0) {
        sb.append("|");
      }
      boolean appendWildcard = original.endsWith("*");
      if (appendWildcard) {
        original = original.substring(0, original.length() - 1);
      }
      sb.append(Pattern.quote(original));
      if (appendWildcard) {
        sb.append(".*");
      }
    }
    return Pattern.compile(sb.toString());
  }

  public String getContextId() {
    return contextId;
  }
  public Set<String> getAllowedClauses() {
    return allowedClauses;
  }
  public Set<String> getAllowedFilters() {
    return allowedFilters;
  }
}
