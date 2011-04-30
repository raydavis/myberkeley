#!/usr/bin/env ruby

require 'logger'

module SlingSites

  class Site

    attr_reader :path

    def initialize(sling, path)
      @sling = sling
      @path = path
    end
	

    def add_group(groupname)
      return @sling.execute_post(@sling.url_for("#{@path}.authorize.json"), "addauth" => groupname)
    end

    def set_joinable(joinable)
      return @sling.execute_post(@sling.url_for(@path), "sakai:joinable" => joinable)
    end
  
    def join(groupname)
      return @sling.execute_post("#{@sling.url_for(@path)}.join.html", "targetGroup" => groupname)
    end

    def self.get_groups(path, sling)
      props = sling.get_node_props(path)
      groups = props["sakai:authorizables"]
      if (groups == nil)
        return []
      else
        case groups
        when String
          return [groups]
        end
        return groups
      end
    end
  
    def get_members
      return @sling.get_node_props("#{@path}.members")
    end

  end

  class SiteManager

    attr_accessor :log

    def initialize(sling)
      @sling = sling
      @log = Logger.new(STDOUT)
      @log.level = Logger::WARN
    end

# This will only create sites on nodes that already exist or nodes that 
# are under a sakai/sites node
# If posting to an existing node sitepath is ignored, if posting to sakai/site node
# sitepath is used.

    def create_site(sitecontainer, title = "Test Site", sitepath = "", sitetemplate=nil )
	  path = @sling.url_for(sitecontainer)
      res = @sling.execute_post(path+".createsite.json", "sakai:title" => title, ":sitepath" => sitepath, "sakai:site-template" => sitetemplate )
      if (res.code != "200" && res.code != "201")
        @log.info "Unable to create site: #{res.code} #{res.body}"
        return nil
      end
      return Site.new(@sling, sitecontainer+sitepath)
    end

    def delete_site(path)
      return @sling.delete_node(path)
    end

    def get_membership
      return @sling.get_node_props("system/sling/membership")
    end

  end

end
