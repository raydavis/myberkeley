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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.util.DateUtils;
import org.sakaiproject.nakamura.util.PathUtils;

import java.util.Map;

@Component(immediate = true, metatype = true)
@Properties(value = {
        @Property(name = "service.vendor", value = "The Sakai Foundation"),
        @Property(name = "service.description", value = "Processor for notification search results."),
        @Property(name = "sakai.search.processor", value = "Notification"),
        @Property(name = "sakai.seach.resourcetype", value = "myberkeley/notification")})
@Service
public class NotificationSearchResultProcessor implements SolrSearchResultProcessor {

  @Reference
  SolrSearchServiceFactory searchServiceFactory;

  public void writeResult(SlingHttpServletRequest request, JSONWriter write, Result result) throws JSONException {
    try {
      Session session = StorageClientUtils.adaptToSession(request.getResourceResolver()
              .adaptTo(javax.jcr.Session.class));
      ContentManager cm = session.getContentManager();

      Content content = cm.get(result.getPath());
      if (content == null) {
        return;
      }
      write.object();
      writeNodeContentsToWriterWithJSONUnpacking(write, content);
      write.endObject();

    } catch (StorageClientException e) {
      throw new RuntimeException(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
    return this.searchServiceFactory.getSearchResultSet(request, query);
  }

  /**
   * Copy of ExtendedJSONWriter.writeNodeContentsToWriter, except that this method will take strings that represent
   * JSON content and expand them into real JSON objects.
   */
  private void writeNodeContentsToWriterWithJSONUnpacking(JSONWriter write, Content content)
          throws JSONException {
    // Since removal of bigstore we add in jcr:path and jcr:name
    write.key("jcr:path");
    write.value(PathUtils.translateAuthorizablePath(content.getPath()));
    write.key("jcr:name");
    write.value(StorageClientUtils.getObjectName(content.getPath()));

    Map<String, Object> props = content.getProperties();
    for (Map.Entry<String, Object> prop : props.entrySet()) {
      String propName = prop.getKey();
      Object propValue = prop.getValue();

      if ("_path".equals(propName)) {
        continue;
      }

      write.key(propName);
      if (propValue instanceof Object[]) {
        write.array();
        for (Object value : (Object[]) propValue) {
          if (isUserPath(propName, value)) {
            write.value(PathUtils.translateAuthorizablePath(value));
          } else {
            write.value(value);
          }
        }
        write.endArray();
      } else if (propValue instanceof java.util.Calendar) {
        write.value(DateUtils.iso8601((java.util.Calendar) propValue));
      } else {
        if (isUserPath(propName, propValue)) {
          write.value(PathUtils.translateAuthorizablePath(propValue));
        } else {
          if (propValue instanceof String) {
            try {
              JSONObject jsonObject = new JSONObject((String) propValue);
              write.value(jsonObject);
              continue;
            } catch (JSONException ignored) {
              // it might be a JSON array
              try {
                JSONArray jsonArray = new JSONArray((String) propValue);
                write.value(jsonArray);
                continue;
              } catch (JSONException alsoignored) {
                // it's neither JSON obj nor JSON array, so just write as a regular value
              }
            }
          }
          write.value(propValue);
        }
      }
    }
  }

  private static boolean isUserPath(String name, Object value) {
    if ("jcr:path".equals(name) || "path".equals(name) || "userProfilePath".equals(name)) {
      String s = String.valueOf(value);
      if (s != null && s.length() > 4) {
        if (s.charAt(0) == '/' && s.charAt(1) == '_') {
          if (s.startsWith("/_user/") || s.startsWith("/_group/") || s.startsWith("a:")) {
            return true;
          }
        }
      }
    }
    return false;
  }

}
