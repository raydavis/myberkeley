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
  puts "#{email}\n"
end

def collect_accounts(sling, username, lists)
  res = sling.execute_get(sling.url_for("~#{username}/_myberkeley-demographic.2.json"))
  if (res.code.to_i > 299)
    @log.error("#{res.code} / #{res.body}")
    return
  end
  json = JSON.parse(res.body)
  demographics = json["myb-demographics"]
  colleges = []
  rex = /^\/colleges\/([A-Za-z0-9_\s]+)\//
  demographics.each do |demographic|
    matches = rex.match(demographic)
    if (matches)
      newcollege = matches[1]
      if !colleges.include?(newcollege)
        colleges.push(newcollege)
      end
    end
  end
  res = sling.execute_get(sling.url_for("~#{username}/public/authprofile.profile.4.json"))
  if (res.code.to_i > 299)
    @log.error("#{res.code} / #{res.body}")
    return
  end
  profile = JSON.parse(res.body)
  emailSection = profile["email"]
  if (!emailSection.nil? && !emailSection["elements"].nil? && !profile["institutional"].nil?)
    email = emailSection["elements"]["email"]["value"]
    colleges.each do |college|
      if (lists[college].nil?)
        lists[college] = []
      end
      lists[college].push(email)
    end
  end
end

@ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(ARGV[0], ARGV[1])
@sling = @ucb_data_loader.sling
remaining_accounts = @ucb_data_loader.get_all_ucb_accounts
email_lists = {}
remaining_accounts.each do |account_uid|
  collect_accounts(@sling, account_uid, email_lists)
end
email_lists.each do |college_name, emails|
  puts "#{college_name}\n"
  emails.each do |email|
    puts "#{email}\n"
  end
  puts "\n"
end
