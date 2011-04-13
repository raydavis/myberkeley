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

package edu.berkeley.myberkeley.notifications.job;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.service.component.ComponentContext;

import java.util.Dictionary;
import java.util.Hashtable;

public class NotificationEmailSenderTest {

  private NotificationEmailSender sender;

  @Mock
  private ComponentContext componentContext;

  public NotificationEmailSenderTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() {
    this.sender = new NotificationEmailSender();
  }

  @Test
  public void activate() throws Exception {
    Dictionary<String, Object> dictionary = new Hashtable<String, Object>();
    dictionary.put(NotificationEmailSender.MAX_RETRIES, 10);
    dictionary.put(NotificationEmailSender.RETRY_INTERVAL, 5);
    dictionary.put(NotificationEmailSender.SEND_EMAIL, false);
    dictionary.put(NotificationEmailSender.SMTP_PORT, 25);
    dictionary.put(NotificationEmailSender.SMTP_SERVER, "localhost");
    when(componentContext.getProperties()).thenReturn(dictionary);
    this.sender.activate(componentContext);
  }

  @Test
  public void deactivate() throws Exception {
    this.sender.deactivate(componentContext);
  }

}
