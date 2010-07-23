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
package edu.berkeley.myberkeley.foreignprincipal;

/**
 *
 */
public interface ForeignPrincipal {
  /**
   * The Principal name (and Jackrabbit Group ID) used to indicate an authenticated
   * Principal who lacks a matching Jackrabbit Authorizable.
   */
  final static String FOREIGN_PRINCIPAL_ID = "sakai:foreignPrincipal";

  /**
   * Dynamic principal managers are only checked if an ACE refers to a principal
   * whose ID matches a Jackrabbit Authorizable that has this property set to "true".
   *
   * TODO This constant is used by the DynamicACLProvider and in other areas of the
   * code base but is not yet centrally exposed.
   */
  final static String DYNAMIC_PRINCIPAL_PROPERTY = "dynamic";
}
