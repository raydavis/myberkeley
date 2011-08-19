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

def output_email(sling, username)
  res = sling.execute_get(sling.url_for("~#{username}/public/authprofile/email.2.json"))
  if (res.code.to_i > 299)
    @log.error("#{res.code} / #{res.body}")
    return
  end
  json = JSON.parse(res.body)
  email = json["elements"]["email"]["value"]
  puts "#{email}\n"
end

@ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(ARGV[0], ARGV[1])
@sling = @ucb_data_loader.sling
remaining_accounts = @ucb_data_loader.get_all_ucb_accounts
remaining_accounts.each do |account_uid|
	output_email(@sling, account_uid)
end
