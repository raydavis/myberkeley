#!/usr/bin/env ruby
require 'rubygems'
require 'json'
require 'digest/sha1'
require 'sling/sling'
require 'lib/sling/users'
require 'lib/sling/authz'
include SlingInterface
include SlingUsers

module MyBerkeleyData
  
  ENV_PROD = 'prod'
  CED_ADVISORS_GROUP_NAME = "g-ced-advisors"
  CED_ALL_STUDENTS_GROUP_NAME = "g-ced-students"
  MAJORS = ["ARCHITECTURE", "CITY REGIONAL PLAN", "DESIGN", "LIMITED", "LANDSCAPE ARCH", "LAND ARCH AND ENV PLAN", "URBAN DESIGN", "URBAN STUDIES"]
  UNDERGRAD_MAJORS = [ "ARCHITECTURE", "INDIVIDUAL", "LIMITED","LANDSCAPE ARCH", "URBAN STUDIES" ]
	GRAD_MAJORS = [ "ARCHITECTURE", "CITY REGIONAL PLAN", "DESIGN","LIMITED", "LAND ARCH AND ENV PLAN", "URBAN DESIGN" ]
  
  CALNET_TEST_USER_IDS = ["test-300846","test-300847","test-300848","test-300849","test-300850","test-300851","test-300852","test-300853","test-300854",
                        "test-300855","test-300856","test-300857","test-300858","test-300859","test-300860","test-300861","test-300862","test-300863",
                        "test-300864","test-300865","test-300866","test-300867","test-300868","test-300869","test-300870","test-300871","test-300872",
                        "test-300873","test-300874","test-300875","test-300876","test-300877"]
  
  CALNET_EMAIL_TEST_USER_IDS = []
    
    TEST_EMAIL_ADDRESSES = ["omcgrath@berkeley.edu", "johnk@media.berkeley.edu"]
    
    TEST_EMAIL_CONTEXT = "g-ced-students-testemail"
  
  class SlingDataLoader
  
    TEST_USER_PREFIX = 'testuser'
    
    @ced_advisors_group = nil                                                                               
    @ced_all_students_group = nil
    
    @env = nil
    
    @user_manager = nil
    @sling = nil
    @authz = nil
    attr_reader :user_password_key, :num_students, :ced_advisors_group, :ced_all_students_group, :authz
  
    def initialize(server, admin_password="admin", numusers="32")
      @num_students = numusers.to_i
      @sling = Sling.new(server, admin_password, true)
      @sling.do_login
      @user_manager = UserManager.new(@sling)
      @authz = SlingAuthz::Authz.new(@sling)
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
    
    def make_password(name, key)
      digest = Digest::SHA1.new
      digest.update(name).update(key)
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
    
    def load_defined_user_advisors
      loaded_advisors = load_defined_users "json_data.js"
      loaded_advisors.each do |loaded_advisor|
        add_advisor_to_group loaded_advisor
        apply_advisor_aces loaded_advisor
      end      
    end
      
    def load_defined_users(json_file_name)
      all_data = JSON.load(File.open json_file_name, "r")
      users = all_data['users']
      loaded_users = Array.new
      users.each do |user|
        puts "creating user: #{user.inspect}"
        loaded_user = load_defined_user user
        puts "loaded user: #{loaded_user.inspect}"
        loaded_users << loaded_user
      end
      return loaded_users
    end
    
    def load_defined_user user
        username = user[0]
        user_props = user[1]
        user_props['context'] = ['g-ced-advisors'] 
        user_props['standing'] = 'advisor'  
        user_props['major'] = ["N/A"]
        user_props['current'] = true
        user_props['participant'] = true
        puts "creating user: #{user.inspect}"
        loaded_user = load_user username, user_props
        return loaded_user
    end
  
    def load_random_users(first_names_file, last_names_file)
      first_names = File.open(first_names_file, "r").readlines
      last_names = File.open(last_names_file, "r").readlines
      all_users_props = generate_all_user_props first_names, last_names
      all_users_props.each do |user_props|
        username = user_props[':name']
        puts "creating user: #{username} with properties: #{user_props.inspect}"
        loaded_random_user = load_user username, user_props
        add_student_to_group loaded_random_user
        apply_student_aces loaded_random_user
      end
    end
    
    def load_calnet_test_users
      i = 0
      CALNET_TEST_USER_IDS.each do |id|
        first_name = id.split('-')[0].to_s
        last_name = id.split('-')[1].to_s
        uid = id.split('-')[1].to_s
        # for a user like test-212381, the calnet uid will be 212381
        user_props = generate_user_props uid, first_name, last_name, i, CALNET_TEST_USER_IDS.length
        loaded_calnet_test_user = load_user uid, user_props
        add_student_to_group loaded_calnet_test_user
        apply_student_aces loaded_calnet_test_user
        i = i + 1
      end
    end
    
    def generate_user_props(username, first_name, last_name, index, length)
        user_props = {}
        user_props[':name'] = username
        user_props['firstName'] = first_name.chomp
        user_props['lastName'] = last_name.chomp  
        user_props['email'] = first_name.downcase + '.' + last_name.downcase + '@berkeley.edu'
        user_props['context'] = ['g-ced-students']
        user_props['major'] = MAJORS[index % 8].sub(/&/, 'AND')
        if ( index < length/2)
          user_props['standing'] = 'undergrad'
          user_props['major'] = UNDERGRAD_MAJORS[index % UNDERGRAD_MAJORS.length].sub(/&/, 'AND')
        else
          user_props['standing'] = 'grad'
          user_props['major'] = GRAD_MAJORS[index % GRAD_MAJORS.length].sub(/&/, 'AND')     
        end
        user_props['current'] = true
        user_props['participant'] = true
        return user_props
    end
    
    def generate_email_user_props(username, first_name, last_name, index, length)
        email_user_props = {}
        email_user_props[':name'] = username
        email_user_props['firstName'] = first_name.chomp
        email_user_props['lastName'] = last_name.chomp  
        email_user_props['email'] = TEST_EMAIL_ADDRESSES[index % 2]
        email_user_props['context'] = [TEST_EMAIL_CONTEXT]
        email_user_props['major'] = MAJORS[index % 8].sub(/&/, 'AND')
        if ( index < length/2)
          email_user_props['standing'] = 'undergrad'
        else
          email_user_props['standing'] = 'grad'     
        end
        email_user_props['current'] = true
        email_user_props['participant'] = true
        return email_user_props
    end
  
    def generate_all_user_props(first_names, last_names)
      i = 0
      all_users_props = []
      while i < @num_students
        user_props = {}
        username = TEST_USER_PREFIX + i.to_s
        first_name = first_names[rand(first_names.length)]
        last_name = last_names[rand(last_names.length)]
        user_props[':name'] = username
        user_props['firstName'] = first_name.chomp!
        user_props['lastName'] = last_name.chomp!
        user_props['email'] = first_name.downcase + '.' + last_name.downcase + '@berkeley.edu'
        user_props['context'] = ['g-ced-students']
        user_props['major'] = MAJORS[i % 8].sub(/&/, 'AND')
        if ( i < @num_students/2)
          user_props['standing'] = 'undergrad'
        else
          user_props['standing'] = 'grad'     
        end
        user_props['current'] = true
        user_props['participant'] = true
        all_users_props[i] = user_props
        i = i + 1
      end
      puts all_users_props.inspect
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
  
    def update_user(username, user_props, password=nil)
      if (password)
          target_user = User.new username, password
        else
          target_user = User.new username
        end      
      target_user.update_profile_properties @sling, user_props
    end
  
    def apply_student_aces(student)
      home_path = student.home_path_for @sling
      @authz.delete(home_path, "everyone")
      @authz.grant(home_path,"everyone","jcr:read" => "denied","jcr:write" => "denied")
      @authz.delete(home_path, "anonymous")
      @authz.grant(home_path,"anonymous","jcr:read" => "denied","jcr:write" => "denied")
      @authz.grant(home_path,"g-ced-advisors","jcr:read" => "granted")
    end
    
    def apply_advisor_aces(advisor)
      home_path = advisor.home_path_for @sling
      @authz.delete(home_path, "everyone")
      @authz.grant(home_path,"everyone","jcr:read" => "denied","jcr:write" => "denied")
      @authz.delete(home_path, "anonymous")
      @authz.grant(home_path,"anonymous","jcr:read" => "denied","jcr:write" => "denied")
      @authz.grant(home_path,"g-ced-students","jcr:read" => "granted") #needed so message search results can include sender's profile info
      @authz.grant(home_path,"g-ced-advisors","jcr:all" => "granted")
    end
    
  end
end

if ($PROGRAM_NAME.include? 'sling_data_loader.rb')
  puts "will load data on server #{ARGV[0]}"
  puts "will attempt to create or update #{ARGV[2]} users"
  sdl = MyBerkeleyData::SlingDataLoader.new ARGV[0], ARGV[1], ARGV[2]
  sdl.get_or_create_groups
  sdl.load_defined_user_advisors #now loading all the project members as advisors same as load_defined_users except adding to g-ced-advisors
  sdl.load_calnet_test_users
end