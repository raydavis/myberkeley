#!/usr/bin/env ruby

# Modify migrated 1.0.2 simplegroup Worlds to work around MYB-1354 & SAKIII-4842.
#   - Add umbrella-group members to a role-holding group if not there.
#   - Remove umbrella-group members.
#   - Change sakai:template from "simplegroup" to "simple-group".

require 'rubygems'
require 'optparse'
require 'active_record'
require 'oci8'
require 'json'
require 'digest/sha1'
require 'logger'
require 'nakamura'
require 'nakamura/users'
require_relative 'ucb_data_loader'

## Block sling.rb's monkeying with form values.
module Net::HTTPHeader
  def encode_kvpair(k, vs)
    if vs.nil? or vs == '' then
      "#{urlencode(k)}="
    elsif vs.kind_of?(Array)
      # In Ruby 1.8.7, Array(string-with-newlines) will split the string
      # after each embedded newline.
      Array(vs).map { |v| "#{urlencode(k)}=#{urlencode(v.to_s)}" }
    else
      "#{urlencode(k)}=#{urlencode(vs.to_s)}"
    end
  end
end

module MyBerkeleyData
  class SimpleGroupFixer
    attr_reader :ucb_data_loader

    def initialize(options)
      @log = Logger.new(STDOUT)
      @log.level = Logger::DEBUG

      @world_groups = []
      @old_groups = []

      @ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(options[:appserver], options[:adminpwd])
      @sling = @ucb_data_loader.sling
    end

    def collect_world_groups
      res = @sling.execute_get(@sling.url_for("var/search/groups-all.json"), {
          "q" => "*",
          "category" => "group",
          "page" => "0",
          "items" => "1000"
      })
      @log.info("group search returned #{res.code}, #{res.body}")
      if (res.code.to_i > 299)
        @log.error("Could not retrieve groups: #{res.code}, #{res.body}")
        return
      end
      json = JSON.parse(res.body)
      @world_groups = json["results"]
      @world_groups.each do |group_rec|
        groupid = group_rec["sakai:group-id"]
        if (!groupid.nil?)
          res = @sling.execute_get(@sling.url_for("system/userManager/group/#{groupid}.json"))
          if (res.code.to_i > 299)
            @log.error("Could not find group #{groupid}: #{res.code}, #{res.body}")
          else
            json = JSON.parse(res.body)
            templateid = json["properties"]["sakai:templateid"]
            umbrella_members = json["members"]
            if (templateid == "simplegroup")
              @old_groups.push(groupid)
              res = @sling.execute_get(@sling.url_for("system/userManager/group/#{groupid}-member.json"))
              if (res.code.to_i > 299)
                @log.error("Could not find group: #{res.code}, #{res.body}")
                return
              end
              member_role_members = JSON.parse(res.body)["members"]
              res = @sling.execute_get(@sling.url_for("system/userManager/group/#{groupid}-manager.json"))
              if (res.code.to_i > 299)
                @log.error("Could not find group: #{res.code}, #{res.body}")
                return
              end
              manager_role_members = JSON.parse(res.body)["members"]
              @log.info("World #{groupid}\n  umbrella_members = #{umbrella_members.inspect}\n" +
                            "  member_role_members = #{member_role_members.inspect}\n" +
                            "  manager_role_members = #{manager_role_members.inspect}")
              umbrella_members.each do |member_id|
                if (!member_id.start_with?(groupid))
                  if (!member_role_members.include?(member_id) && (!manager_role_members.include?(member_id)))
                    @log.info("About to re-add member #{member_id}")
                    res = @sling.execute_post(@sling.url_for("system/userManager/group/#{groupid}-member.update.html"), {
                        ":member" => member_id
                    })
                    if (res.code.to_i > 299)
                      @log.error("Could not update: #{res.code}, #{res.body}")
                      return
                    end
                  end
                  @log.info("About to remove #{member_id} from the umbrella group")
                  res = @sling.execute_post(@sling.url_for("system/userManager/group/#{groupid}.update.html"), {
                      ":member@Delete" => member_id,
                      ":viewer@Delete" => member_id
                  })
                  if (res.code.to_i > 299)
                    @log.error("Could not update: #{res.code}, #{res.body}")
                    return
                  end
                end
              end
              @log.info("About to update templateid of #{groupid}")
              res = @sling.execute_post(@sling.url_for("system/userManager/group/#{groupid}.update.html"), {
                  "sakai:templateid" => "simple-group"
              })
              if (res.code.to_i > 299)
                @log.error("Could not update: #{res.code}, #{res.body}")
                return
              end
            end
          end
        end
      end
      @log.info("Fixed the following World groups: #{@old_groups.inspect}")
    end
  end
end

  options = {}
  optparser = OptionParser.new do |opts|
    opts.banner = "Usage: MYB-1354.rb [options]"
    # trailing slash is mandatory
    options[:appserver] = "http://localhost:8080/"
    opts.on("-a", "--appserver [APPSERVE]", "Application Server") do |as|
      options[:appserver] = as
    end

    options[:adminpwd] = "admin"
    opts.on("-q", "--adminpwd [ADMINPWD]", "Application Admin User Password") do |ap|
      options[:adminpwd] = ap
    end
  end


optparser.parse ARGV

odl = MyBerkeleyData::SimpleGroupFixer.new options

odl.collect_world_groups
