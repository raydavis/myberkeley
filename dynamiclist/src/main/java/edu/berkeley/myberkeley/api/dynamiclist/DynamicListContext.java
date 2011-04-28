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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

public class DynamicListContext {
  private String contextId;
  private Set<String> allowedClauses;

  /**
   * For testing only.
   */
  public DynamicListContext(String contextId, Set<String> allowedClauses) {
    this.contextId = contextId;
    this.allowedClauses = allowedClauses;
  }

  public DynamicListContext(Node node) throws RepositoryException {
    this.contextId = node.getProperty(DynamicListService.DYNAMIC_LIST_CONTEXT_PROP).getString();
    Value[] vals = JcrUtils.getValues(node, DynamicListService.DYNAMIC_LIST_CONTEXT_CLAUSES_PROP);
    this.allowedClauses = new HashSet<String>(vals.length);
    for (Value val : vals) {
      this.allowedClauses.add(val.getString());
    }
  }

  public String getContextId() {
    return contextId;
  }
  public Set<String> getAllowedClauses() {
    return allowedClauses;
  }
}
