#!/usr/bin/env ruby
require 'rubygems'
require 'json'
require 'sling/sling'
require 'lib/sling/users'
include SlingInterface
include SlingUsers

module MyBerkeleyData
  
  ENV_PROD = 'prod'
  CED_ADVISORS_GROUP_NAME = "g-ced-advisors"
  CED_ALL_STUDENTS_GROUP_NAME = "g-ced-students"
  MAJORS = ["ARCHITECTURE", "CITY REGIONAL PLAN", "DESIGN", "LIMITED", "LANDSCAPE ARCH", "LAND ARCH & ENV PLAN", "URBAN DESIGN", "URBAN STUDIES"]

  class SlingDataLoader
  
    TEST_USER_PREFIX = 'testuser'
    
    @ced_advisors_group = nil
    @ced_all_students_group = nil
    
    @env = nil
    
    @user_manager = nil
    @sling = nil
      
    attr_reader :user_password_key, :num_students, :ced_advisors_group, :ced_all_students_group
  
    def initialize(server, admin_password="admin", numusers="20", user_password_key=nil)
      @num_students = numusers.to_i
      @user_password_key = user_password_key
      @sling = Sling.new(server, admin_password, true)
      @sling.do_login
      @user_manager = UserManager.new(@sling)
    end
      
    def get_or_create_groups
      @ced_advisors_group = get_or_create_group CED_ADVISORS_GROUP_NAME
      @ced_all_students_group = get_or_create_group CED_ALL_STUDENTS_GROUP_NAME
    end
    
    def get_or_create_group(groupname)
      group = @user_manager.create_group groupname
      if(!group)
        group = Group.new groupname
      end
      return group
    end
    
    def make_password (ed_student, key)
      digest = Digest::SHA1.new
      digest.update(ced_student.stu_name).update(key)
      return digest.hexdigest
    end
    
    def add_student_to_group user
      @ced_all_students_group.add_member @sling, user.name, "user"
      user_props = @user_manager.get_user_props user.name
    end
    
    def add_advisor_to_group advisor
      @ced_advisors_group.add_member @sling, advisor.name, "user"
      user_props = @user_manager.get_user_props advisor.name
    end
    
    def load_advisors
      all_data = JSON.load(File.open "json_data.js", "r")
      advisors = all_data['users']
      advisors.each do |advisor|
        username = advisor[0]
        user_props = advisor[1]
        puts "creating advisor: #{advisor.inspect}"
        loadedAdvisor = load_user username, user_props
        add_advisor_to_group loadedAdvisor
      end      
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
        loaded_user = load_user username, user_props
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
        loaded_random_user = load_user username, user_props
        add_student_to_group loaded_random_user
      end
    end
  
    def generate_user_props(first_names, last_names)
      i = 0
      all_users_props = []
      while i <= @num_students
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
          user_props['major'] = MAJORS[i % 8]
        else
          user_props['standing'] = 'grad'
          index = (i % 8) - 1
          index = 7 if (index == -1)
          user_props['major'] = MAJORS[index]      
        end
        user_props['current'] = true
        user_props['participant'] = true
        all_users_props[i] = user_props
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
end

if ($PROGRAM_NAME.include? 'sling_data_loader.rb')
  puts "will load data on server #{ARGV[0]}"
  puts "will attempt to create or update #{ARGV[2]} users"
  sdl = MyBerkeleyData::SlingDataLoader.new ARGV[0], ARGV[1], ARGV[2]
  sdl.get_or_create_groups
  sdl.load_advisors #now loading all the project members as advisors same as load_defined_users except adding to g-ced-advisors
  sdl.load_defined_users "json_data.js"
  sdl.load_random_users "firstNames.txt", "lastNames.txt"
end