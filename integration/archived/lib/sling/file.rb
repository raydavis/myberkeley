#!/usr/bin/env ruby

module SlingFile

  $testfile1 = "<html><head><title>KERN 312</title></head><body><p>Should work</p></body></html>"

  class FileManager

    def initialize(sling)
      @sling = sling
    end

    def createlink(url, linkUid, siteUuid)
      props = {
        ":operation" => "link",
        "link" => linkUid
      }
      if (siteUuid != nil)
        props.update("site" => siteUuid)
      end
      return @sling.execute_post(@sling.url_for(url), props)
    end

    def createTag(tagName, url, props = {})
      props.update("./jcr:primaryType" => "nt:folder")
      props.update("./jcr:mixinTypes" => "sakai:propertiesmix")
      props.update("./sling:resourceType" => "sakai/tag")
      props.update("./sakai:tag-name" => tagName)
      return @sling.execute_post(@sling.url_for(url), props)
    end

    def tag(url, tagUuid)
      props = {
        ":operation" => "tag",
        "key" => tagUuid
      }
      return @sling.execute_post(@sling.url_for(url), props)
    end

    def myfiles(search)
      return @sling.execute_get(@sling.url_for("/var/search/files/myfiles.json?q=#{search}"))
    end

    def upload_pooled_file(name, data, content_type, toid=nil)
      if ( toid == nil )
         return @sling.execute_file_post(@sling.url_for("/system/pool/createfile"), name, name, data, content_type)
      else
         return @sling.execute_file_post(@sling.url_for("/system/pool/createfile.#{toid}"), name, name, data, content_type)
      end
    end

    def url_for_pooled_file(id)
      return @sling.url_for("/p/#{id}")
    end

    # Members
    def get_members(id)
      path = "#{url_for_pooled_file(id)}.members.json"
      return @sling.execute_get(path)
    end

    def manage_members(id, add_viewers, delete_viewers, add_managers, delete_managers)
      path = "#{url_for_pooled_file(id)}.members.html"
      params = {}
      params[":viewer"] ||= add_viewers
      params[":viewer@Delete"] ||= delete_viewers
      params[":manager"] ||= add_managers
      params[":manager@Delete"] ||= delete_managers
      return @sling.execute_post(path, params)
    end

    # Search templates

    def search_my_managed(q)
      return @sling.execute_get(@sling.url_for("/var/search/pool/me/manager.json?q=#{q}"))
    end

    def search_my_viewed(q)
      return @sling.execute_get(@sling.url_for("/var/search/pool/me/viewer.json?q=#{q}"))
    end

  end
end
