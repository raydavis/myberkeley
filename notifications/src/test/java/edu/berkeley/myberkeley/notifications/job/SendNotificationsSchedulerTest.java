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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.berkeley.myberkeley.api.dynamiclist.DynamicListService;
import edu.berkeley.myberkeley.notifications.NotificationTests;
import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.lite.Repository;

import java.io.Serializable;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

public class SendNotificationsSchedulerTest extends NotificationTests {

  private SendNotificationsScheduler sender;

  @Mock
  private ComponentContext context;

  public SendNotificationsSchedulerTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Before
  public void setup() {
    this.sender = new SendNotificationsScheduler();
    this.sender.sparseRepository = mock(Repository.class);
    this.sender.slingRepository = mock(SlingRepository.class);
    this.sender.scheduler = mock(Scheduler.class);
    this.sender.dynamicListService = mock(DynamicListService.class);
  }

  @Test
  public void activate() throws Exception {
    Dictionary<String, Long> dictionary = new Hashtable<String, Long>();
    dictionary.put(SendNotificationsScheduler.PROP_POLL_INTERVAL_SECONDS, 60L);
    when(this.context.getProperties()).thenReturn(dictionary);
    this.sender.activate(this.context);
    verify(this.sender.scheduler).addPeriodicJob(Matchers.<String>any(), Matchers.<SendNotificationsJob>any(),
            Matchers.<Map<String, Serializable>>any(), Matchers.anyLong(), Matchers.anyBoolean());
  }

  @Test
  public void deactivate() throws Exception {
    this.sender.deactivate(this.context);
    verify(this.sender.scheduler).removeJob(SendNotificationsScheduler.JOB_NAME);
  }

}
