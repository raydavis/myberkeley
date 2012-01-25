#!/usr/bin/env ruby

require 'rubygems'
require 'optparse'
require 'json'
require 'logger'
require 'nakamura'
require 'nakamura/users'
require_relative 'ucb_data_loader'

@log = Logger.new(STDOUT)
@log.level = Logger::DEBUG

def print_email(sling, username)
  res = sling.execute_get(sling.url_for("~#{username}/public/authprofile.profile.4.json"))
  if (res.code.to_i > 299)
    @log.error("#{res.code} / #{res.body}")
    return
  end
  profile = JSON.parse(res.body)
  emailSection = profile["email"]
  if (!emailSection.nil? && !emailSection["elements"].nil?)
    email = emailSection["elements"]["email"]["value"]
    puts "#{email}\n"
  else
    @log.error("No email for #{username}")
  end
end

@ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(ARGV[0], ARGV[1])
@sling = @ucb_data_loader.sling
ucbAccounts = @ucb_data_loader.get_all_ucb_accounts
participants = ucbAccounts['participants']
participants.each do |account_uid|
  print_email(@sling, account_uid)
end
