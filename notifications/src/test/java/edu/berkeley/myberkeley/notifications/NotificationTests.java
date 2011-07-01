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

package edu.berkeley.myberkeley.notifications;

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Assert;
import org.sakaiproject.nakamura.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public abstract class NotificationTests extends Assert {

  public String readCalendarNotificationFromFile() throws IOException {
    InputStream in = getClass().getClassLoader().getResourceAsStream("calendarNotification.json");
    return IOUtils.readFully(in, "utf-8");
  }

  public String readMessageNotificationFromFile() throws IOException {
    InputStream in = getClass().getClassLoader().getResourceAsStream("messageNotification.json");
    return IOUtils.readFully(in, "utf-8");
  }

  protected ValueMap buildProfileMap(String emailString) {
    HashMap<String, Object> profile = new HashMap<String, Object>();
    HashMap<String, Object> basic = new HashMap<String, Object>();
    HashMap<String, Object> elements = new HashMap<String, Object>();
    HashMap<String, Object> email = new HashMap<String, Object>();
    email.put("value", emailString);
    elements.put("email", email);
    basic.put("elements", elements);
    profile.put("basic", basic);
    return new ValueMapDecorator(profile);
  }
}
