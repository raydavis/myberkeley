#!/usr/bin/env ruby

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require 'ruby-lib-dir.rb'

require 'rubygems'
require 'optparse'
require 'json'
require 'digest/sha1'
require 'sling/sling'
require 'sling/users'
include SlingInterface
include SlingUsers

# Loading:
#   ruby ucb_big_demographic_load.rb --userids 20k-random-userids.txt --num-demographics 5
#
# Testing:
#   curl -g -u 211159:testuser \
#       "http://localhost:8080/var/myberkeley/dynamiclists/g-big-demographic-test.json?criteria={AND:[\"/colleges/BigTest\",\"/colleges/BigTest/RegistrationStatus/Withdrew\"]}"
#   {"count":1}

module BigDemographicLoad
  class BigDemographicLoader
    ADVISORS_GROUP_NAME = "g-big-demographic-test-advisors"
    CONTEXT_NAME = "g-big-demographic-test"
    ALL_DEMOGRAPHIC = "/colleges/BigTest"
    MOST_DEMOGRAPHIC = "/colleges/BigTest/RegistrationStatus/Registered"
    SOME_DEMOGRAPHIC = "/colleges/BigTest/RegistrationStatus/Withdrew"
    ADVISOR_USER_IDS = ["211159", "904715", "95509"]  # Ray, Chris, Kevin
    STUDENT_ID_FILE = "20k-random-userids.txt"  

    def initialize(options)
      @user_ids_file = options[:usersfile]
      @num_demogs = options[:numdemogs].to_i
      @user_ids = []
      server_url = options[:appserver]
      admin_password = options[:adminpwd]
      @s = Sling.new(server_url, false)
      @s.log.level = Logger::INFO
      @um = UserManager.new(@s)
      @um.log.level = Logger::INFO
      real_admin = User.new("admin", admin_password)
      @s.switch_user(real_admin)
      @s.do_login
      @log = Logger.new(STDOUT)
      @log.level = Logger::DEBUG
    end
    
    def load_users_data
      user_ids_file = File.open(@user_ids_file, "r")
      @user_ids = user_ids_file.readlines.collect{|line| line.chomp}
      user_ids_file.close
    end
    
    def initialize_demographic_context
      advisors_group = @um.create_group(ADVISORS_GROUP_NAME)
      if (advisors_group.nil?)
        advisors_group = Group.new(ADVISORS_GROUP_NAME)
      end
      advisors_group.add_members(@s, ADVISOR_USER_IDS)
      context_url = @s.url_for("/var/myberkeley/dynamiclists/#{CONTEXT_NAME}")
      @s.execute_post(context_url, {
        "sling:resourceType" => "myberkeley/dynamicListContext",
        "myb-context" => CONTEXT_NAME,
        "myb-clauses" => [ALL_DEMOGRAPHIC, MOST_DEMOGRAPHIC, SOME_DEMOGRAPHIC]
      })
      @s.execute_post("#{context_url}.modifyAce.html", {
        "principalId" => "everyone",
        "privilege@jcr:all" => "denied"
      })
      @s.execute_post("#{context_url}.modifyAce.html", {
        "principalId" => "anonymous",
        "privilege@jcr:all" => "denied"
      })
      @s.execute_post("#{context_url}.modifyAce.html", {
        "principalId" => ADVISORS_GROUP_NAME,
        "privilege@jcr:read" => "granted"
      })
    end
    
    def load_demographic_profiles
      # All target users belong to ALL_DEMOGRAPHIC.
      # Three-fourths belong to MOST_DEMOGRAPHIC.
      # One-fourth belongs to SOME_DEMOGRAPHIC.
      (1..@num_demogs).each do |i|
        user_id = @user_ids[i - 1]
        if (i % 4 == 0)
          opt_demographic = SOME_DEMOGRAPHIC
        else
          opt_demographic = MOST_DEMOGRAPHIC
        end
        res = @s.execute_post(@s.url_for("/~#{user_id}.myb-demographic.html"), {
          "myb-demographics" => [ALL_DEMOGRAPHIC, opt_demographic]
        })
        @log.info("/~#{user_id}.myb-demographic: #{res.code} #{res.body}")
      end
    end
  end

if ($PROGRAM_NAME.include? 'ucb_big_demographic_load.rb')
  options = {}
  optparser = OptionParser.new do |opts|
    opts.banner = "Usage: ucb_big_demographic_load.rb [options]"
    # trailing slash is mandatory
    options[:appserver] = "http://localhost:8080/" 
    opts.on("-s", "--server [APPSERVE]", "Application Server") do |as|
      options[:appserver] = as
    end
    options[:adminpwd] = "admin"
    opts.on("-a", "--adminpwd [ADMINPWD]", "Application Admin User Password") do |ap|
      options[:adminpwd] = ap
    end
    opts.on("-u", "--userids USERIDS", "File of user_ids") do |nu|
      options[:usersfile] = nu
    end
    options[:numdemogs] = 0
    opts.on("-n", "--num-demographics NUMDEMOGRAPHICS", "Number of demographic profiles to create") do |ng|
      options[:numdemogs] = ng
    end
  end
  optparser.parse ARGV
  
  sdl = BigDemographicLoad::BigDemographicLoader.new options
  sdl.load_users_data
  sdl.initialize_demographic_context
  sdl.load_demographic_profiles
end

end