#!/usr/bin/env ruby

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require 'ruby-lib-dir.rb'

require 'rubygems'
require 'optparse'
require 'json'
require 'logger'
require 'sling/sling'
require 'ucb_data_loader'

@log = Logger.new(STDOUT)
@log.level = Logger::DEBUG

def fix_MYB_986_email_protection(sling, username)
  res = sling.execute_get(sling.url_for("~#{username}/public/authprofile/email.acl.json"))
  acls = JSON.parse(res.body)
  if (1 == acls[username]["granted"].length)
    @log.info("About to fix permissions for #{username}")
    result = @sling.execute_post(sling.url_for("~#{username}/public/authprofile/email.modifyAce.html"), {
      "principalId" => username,
      "privilege@jcr:all" => "granted"
    })
    @log.error("#{result.code} / #{result.body}") if (result.code.to_i > 299)
  end
end

@ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(ARGV[0], ARGV[1])
@sling = @ucb_data_loader.sling
remaining_accounts = @ucb_data_loader.get_all_ucb_accounts
remaining_accounts.each do |account_uid|
	fix_MYB_986_email_protection(@sling, account_uid)
end
