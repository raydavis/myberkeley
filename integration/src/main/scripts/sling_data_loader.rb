#!/usr/bin/env ruby
require 'rubygems'
require 'json'
require 'sling/sling'
require 'sling/users'
include SlingInterface
include SlingUsers

class SlingDataLoader

  TEST_USER_PREFIX = 'testuser'

  def initialize(server="http://localhost:8080/", numusers="20")
    @num_users = numusers.to_i
    @sling = Sling.new(server, true, true)
    @sling.do_login
    @user_manager = UserManager.new(@sling)
  end

  def load_defined_users(json_file_name)
    all_data = JSON.load(File.open json_file_name, "r")
    users = all_data['users']
    users.each do |user|
      username = user[0]
      user_props = user[1]
      puts "creating user: #{user.inspect}"
      load_user username, user_props
    end
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
    i = 0
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
      all_users_props[i] = user_props
      i = i + 1
    end
    return all_users_props
  end

  def load_user(username, user_props)
    target_user = @user_manager.create_user_with_props username, user_props
    # if user exists, they will not be (re)created but props may be updated
    if (target_user.nil?)
      puts "user #{username} not created, may already exist, attempting ot update properties of user: #{user_props.inspect}"
      target_user = User.new username
      target_user.update_properties @sling, user_props
    end
  end
end

if $PROGRAM_NAME == $0
  puts "will load data on server #{ARGV[0]}"
  puts "will attempt to create or update #{ARGV[1]} users"
  sdl = SlingDataLoader.new ARGV[0], ARGV[1]
  sdl.load_defined_users "json_data.js"
  sdl.load_random_users "firstNames.txt", "lastNames.txt"
end