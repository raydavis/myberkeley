#!/usr/bin/env ruby
require 'rubygems'
require 'json'
require 'sling/sling'
require 'lib/sling/users'
include SlingInterface
include SlingUsers

class SlingDataLoader

  TEST_USER_PREFIX = 'testuser'
  MAJORS = ["ARCHITECTURE", "CITY REGIONAL PLAN", "DESIGN", "LIMITED", "LANDSCAPE ARCH", "LAND ARCH & ENV PLAN", "URBAN DESIGN", "URBAN STUDIES"]

  def initialize(server, admin_password="admin", numusers="20")
    @num_users = numusers.to_i
    @sling = Sling.new(server, admin_password, true)
    @sling.do_login
    @user_manager = UserManager.new(@sling)
  end

  def load_defined_users(json_file_name)
    all_data = JSON.load(File.open json_file_name, "r")
    users = all_data['users']
    users.each do |user|
      username = user[0]
      user_props = user[1]
      user_props['context'] = ['g-ced-advisors'] 
      user_props['standing'] = 'advisor'  
      user_props['major'] = ["N/A"]
      user_props['current'] = true
      user_props['participant'] = true
      puts "creating user: #{user.inspect}"
      load_user username, user_props
    end
    return users
  end

  def load_random_users(first_names_file, last_names_file)
    first_names = File.open(first_names_file, "r").readlines
    last_names = File.open(last_names_file, "r").readlines
    all_users_props = generate_user_props first_names, last_names
    all_users_props.each do |user_props|
      username = user_props[':name']
      puts "creating user: #{username} with properties: #{user_props.inspect}"
      load_user username, user_props
    end
  end

  def generate_user_props(first_names, last_names)
    i = 31
    all_users_props = []
    while i <= @num_users
      user_props = {}
      username = TEST_USER_PREFIX + i.to_s
      first_name = first_names[rand(first_names.length)]
      last_name = last_names[rand(last_names.length)]
      user_props[':name'] = username
      user_props['firstName'] = first_name.chomp!
      user_props['lastName'] = last_name.chomp!
      user_props['email'] = first_name.downcase + '.' + last_name.downcase + '@berkeley.edu'
      user_props['context'] = ['g-ced-students']
      if ( i % 2 == 0)
        user_props['standing'] = 'undergrad'
      else
        user_props['standing'] = 'grad'  
      end
      user_props['major'] = MAJORS[i % 8]
      user_props['current'] = true
      user_props['participant'] = true
      all_users_props[@num_users - i] = user_props
      i = i + 1
    end
    return all_users_props
  end

  def load_user(username, user_props, password=nil)
    target_user = @user_manager.create_user_with_props username, user_props, password
    # if user exists, they will not be (re)created but props may be updated
    if (target_user.nil?)
      puts "user #{username} not created, may already exist, attempting to update properties of user: #{user_props.inspect}"
      if (password)
        target_user = User.new username, password
      else
        target_user = User.new username
      end      
      target_user.update_profile_properties @sling, user_props
    end
    return target_user
  end
end

if ($PROGRAM_NAME.include? 'sling_data_loader.rb')
  puts "will load data on server #{ARGV[0]}"
  puts "will attempt to create or update #{ARGV[2]} users"
  sdl = SlingDataLoader.new ARGV[0], ARGV[1], ARGV[2]
  sdl.load_defined_users "json_data.js"
  sdl.load_random_users "firstNames.txt", "lastNames.txt"
end