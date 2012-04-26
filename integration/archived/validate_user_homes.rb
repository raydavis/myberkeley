#!/usr/bin/env ruby

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require 'ruby-lib-dir.rb'

require 'rubygems'
require 'json'
require 'digest/sha1'
require 'sling/sling'
include SlingInterface

USER_ID_FILE = "20k-random-userids.txt"  
server_url = "http://localhost:8080/"

s = Sling.new(server_url, false)
s.log.level = Logger::INFO
s.do_login
log = Logger.new(STDOUT)
log.level = Logger::DEBUG

user_ids_file = File.open(USER_ID_FILE, "r")
user_ids = user_ids_file.readlines.collect{|line| line.chomp}
user_ids_file.close

user_ids.each do |user_id|
  res = s.execute_get(s.url_for("~#{user_id}.json"))
  log.debug("#{user_id} = #{res.code} - #{res.body}")
  begin
    props = JSON.parse(res.body)
    if (props["sling:resourceType"] != "sakai/user-home" || props["_path"] != "a:#{user_id}")
      log.warn("BAD HOME FOR ~#{user_id} : sling:resourceType = #{props["sling:resourceType"]} and _path = #{props["_path"]}")
    end
  rescue JSON::ParserError => e
    log.warn("BAD HOME FOR ~#{user_id} : Body = #{res.body}")    
  end
end
