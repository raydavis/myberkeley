package edu.berkeley.myberkeley.migrators;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component
public class PubspaceMigrator {

  private static final Logger LOG = LoggerFactory.getLogger(PubspaceMigrator.class);

  private static final int PAGE_SIZE = 4000;

  @Reference
  Repository repository;

  @Reference
  SolrServerService solrServerService;

  @Activate
  public void activate(Map<String, Object> props) {
    migrate();
  }

  private void migrate() {

    Session session = null;
    try {
      session = repository.loginAdministrative();
      ContentManager cm = session.getContentManager();
      cm.setMaintanenceMode(true);

      int start = 0;
      int page = 0;
      int processed = 0;
      int upgradeable = 0;
      int upgraded = 0;

      // Search for all users and page through em
      SolrServer server = solrServerService.getServer();
      SolrQuery query = new SolrQuery();
      query.setQuery("type:u");
      query.setRows(PAGE_SIZE);

      QueryResponse response = server.query(query);
      long totalResults = response.getResults().getNumFound();
      LOG.info("Checking {} user homes for pubspace nodes to upgrade.", totalResults);

      while (start < totalResults) {
        query.setStart(start);
        response = server.query(query);
        SolrDocumentList resultDocs = response.getResults();

        for (SolrDocument doc : resultDocs) {
          boolean dirty = false;
          String id = (String) doc.get("id");
          processed++;

          // users with /~user/public/pubspace/structure0 have logged in and need an upgrade
          String pubspacePath = "a:" + id + "/public/pubspace";
          Content pubspace = cm.get(pubspacePath);
          if (pubspace != null) {
            if (pubspace.hasProperty("structure0")) {
              String prop = (String) pubspace.getProperty("structure0");
              upgradeable++;
              LOG.debug("User id {} has an upgradeable pubspace", id);

              JSONObject pubspaceJSON = null;
              JSONObject profile = null;

              try {
                pubspaceJSON = new JSONObject(prop);
                profile = pubspaceJSON.getJSONObject("profile");
              } catch (JSONException e) {
                LOG.error("Malformed json encountered in pubspace for user " + id, e);
              }

              if (profile != null) {
                try {
                  JSONObject locations = profile.getJSONObject("locations");
                  if (!locations.has("_reorderOnly") || !locations.getBoolean("_reorderOnly")) {
                    locations.put("_reorderOnly", Boolean.TRUE);
                    dirty = true;
                  }
                } catch (JSONException ignored) {

                }
              }

              try {
                if (dirty) {
                  LOG.debug("pubspace JSON for user " + id + " after changes: " + pubspaceJSON.toString(2));
                } else {
                  LOG.debug("pubspace JSON for user " + id + " does not need changing: " + pubspaceJSON.toString(2));
                }
              } catch (JSONException ignored) {
              }

              if (dirty) {
                pubspace.setProperty("structure0", pubspaceJSON.toString());
                cm.update(pubspace);
                LOG.info("Successfully updated user " + id + " pubspace at path " + pubspace.getPath());
                upgraded++;
              }

            }
          }

        }

        page++;
        start = page * PAGE_SIZE;
        LOG.info("Processed {} of {}.", processed, totalResults);
      }

      LOG.info("Of " + totalResults + " total users, " + upgradeable + " had pubspace nodes, and " + upgraded + " needed upgrades");

    } catch (AccessDeniedException e) {
      LOG.error("Got permission denied accessing object, which should be impossible since this is an admin session", e);
    } catch (ClientPoolException e) {
      LOG.error("Got client pool exception connecting to sparse storage", e);
    } catch (StorageClientException e) {
      LOG.error("Got an exception from the storage client", e);
    } catch (SolrServerException e) {
      LOG.error("Error doing solr search", e);
    } finally {
      try {
        if (session != null) {
          session.logout();
        }
      } catch (ClientPoolException e) {
        LOG.error("Got exception logging out of admin session", e);
      }
    }


  }
}
