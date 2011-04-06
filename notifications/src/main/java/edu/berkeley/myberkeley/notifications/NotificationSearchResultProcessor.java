package edu.berkeley.myberkeley.notifications;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
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
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;

@Component(immediate = true, metatype = true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = "service.description", value = "Processor for notification search results."),
    @Property(name = "sakai.search.processor", value = "Notification"),
    @Property(name = "sakai.seach.resourcetype", value = "myberkeley/notification") })
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
            ExtendedJSONWriter.writeNodeContentsToWriterWithJSONUnpacking(write, content);
            write.endObject();

        } catch (StorageClientException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (AccessDeniedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
        return searchServiceFactory.getSearchResultSet(request, query);
    }

}
