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


import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;

import java.util.Collection;
import java.util.Set;

import javax.jcr.RepositoryException;

/**
 *
 */
public interface DynamicListService {
  public static final String DYNAMIC_LIST_RT = "myberkeley/dynamiclist";
  public static final String DYNAMIC_LIST_STORE_RT = "myberkeley/dynamicliststore";
  public static final String DYNAMIC_LIST_CONTEXT_RT = "myberkeley/dynamicListContext";
  public static final String DYNAMIC_LIST_PERSONAL_DEMOGRAPHIC_RT = "myberkeley/personalDemographic";
  public static final String DYNAMIC_LIST_CONTEXT_PROP = "myb-context";
  public static final String DYNAMIC_LIST_DEMOGRAPHIC_DATA_PROP = "myb-demographics";
  public static final String DYNAMIC_LIST_CONTEXT_CLAUSES_PROP = "myb-clauses";
  public static final String DYNAMIC_LIST_CONTEXT_FILTERS_PROP = "myb-filters";
  public static final String DYNAMIC_LIST_CONTEXT_WILDCARD = "*";
  public static final String DYNAMIC_LIST_STORE_CONTEXT_PROP = "context";
  public static final String DYNAMIC_LIST_STORE_CRITERIA_PROP = "criteria";

  Collection<String> getUserIdsForCriteria(DynamicListContext context, String criteriaJson);

  Collection<String> getUserIdsForNode(Content node, Session session) throws StorageClientException,
          AccessDeniedException, RepositoryException;

  /**
   * @param session
   * @param userId
   * @param demographicSet null if demographic property should be deleted
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  void setDemographics(Session session, String userId, Set<String> demographicSet) throws StorageClientException, AccessDeniedException;

  Iterable<String> getAllUserIds(Session session) throws StorageClientException, AccessDeniedException;

}
