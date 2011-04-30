#!/usr/bin/env ruby

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require 'ruby-lib-dir.rb'

require 'rubygems'
require 'json'
require 'digest/sha1'
require 'logger'
require 'sling/sling'
require 'sling/users'
require 'sling/authz'
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
    
  UG_GRAD_FLAG_MAP = {:U => 'Undergraduate Student', :G => 'Graduate Student'}
  
  class UcbDataLoader
    TEST_USER_PREFIX = 'testuser'
    
    @ced_advisors_group = nil                                                                               
    @ced_all_students_group = nil
    
    @env = nil
    
    @bedeworkServer = nil
    @bedeworkPort = nil

    @user_manager = nil
    @sling = nil
    @authz = nil
  
    def initialize(server, admin_password="admin", bedeworkServer=nil, bedeworkPort="8080")
      @log = Logger.new(STDOUT)
      @log.level = Logger::DEBUG
      @sling = Sling.new(server, true)
      @user_manager = UserManager.new(@sling)
      real_admin = User.new("admin", admin_password)
      @sling.switch_user(real_admin)
      @sling.do_login
      @authz = SlingAuthz::Authz.new(@sling)
      @server = server
      @bedeworkServer = bedeworkServer
      @bedeworkPort = bedeworkPort
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
        make_advisor_props user_props
        puts "creating user: #{user.inspect}"
        loaded_user = load_user username, user_props
        return loaded_user
    end
  
    def make_advisor_props user_props #need to have firstName, lastName and email loaded already
        user_props['context'] = [CED_ADVISORS_GROUP_NAME] 
        user_props['standing'] = 'advisor'  
        user_props['major'] = ["N/A"]
        user_props['current'] = true
        # user_props['department'] = '' empty string breaks trimpath
        user_props['college'] = ['College of Environmental Design']
        user_props['role'] = ['Staff'] 
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
        apply_student_demographic loaded_calnet_test_user, i, CALNET_TEST_USER_IDS.length
        i = i + 1
      end
    end
      
    def generate_user_props(username, first_name, last_name, index, length)
        user_props = {}
        user_props[':name'] = username
        user_props['firstName'] = first_name.chomp
        user_props['lastName'] = last_name.chomp  
        user_props['email'] = first_name.downcase + '.' + last_name.downcase + '@berkeley.edu'
        user_props['context'] = [CED_ALL_STUDENTS_GROUP_NAME]
        #user_props['department'] = '' # empty string breaks trimpath
        user_props['college'] = ['College of Environmental Design'] 
        user_props['major'] = MAJORS[index % 8].sub(/&/, 'AND')
        if ( index < length/2)
          user_props['standing'] = 'undergrad'
          user_props['role'] = UG_GRAD_FLAG_MAP[:U]
          user_props['major'] = UNDERGRAD_MAJORS[index % UNDERGRAD_MAJORS.length].sub(/&/, 'AND')
        else
          user_props['standing'] = 'grad'
          user_props['role'] = UG_GRAD_FLAG_MAP[:G]
          user_props['major'] = GRAD_MAJORS[index % GRAD_MAJORS.length].sub(/&/, 'AND')     
        end
        user_props['current'] = true
        return user_props
    end

    def create_user_with_props(username, user_props, password=nil)
      @log.info "Creating user: #{username}"
      if (password)
        user = User.new(username, password)
      else
        user = User.new(username)
      end
      data = { ":name" => user.name,
              "pwd" => user.password,
              "pwdConfirm" => user.password
      }
      firstname = user_props['firstName']
      lastname = user_props['lastName']
      if (!firstname.nil? and !lastname.nil?)
        data = add_profile_property(user_props, data)
        data[":sakai:pages-template"] = "/var/templates/site/defaultuser"
      end
      result = @sling.execute_post(@sling.url_for("#{$USER_URI}"), data)
      if (result.code.to_i > 299)
        @log.info "Error creating user"
        return nil
      end
      return user
    end

    def add_profile_property(user_props, post_data)
      email = user_props['email'] || ""
      firstname = user_props['firstName']  || ""
      lastname = user_props['lastName'] || ""
      role = user_props['role'] || ""
      department = user_props['department'] || ""
      college = user_props['college'] || ""
      major = user_props['major'] || ""
      context = user_props['context'] || ""
      standing = user_props['standing'] || ""
      current = user_props['current'] || ""
      post_data[":sakai:profile-import"] = "{ 'basic': { 'access': 'everybody', 'elements': { 'email': { 'value': '#{email}' }, 'firstName': { 'value': '#{firstname}' }, 'lastName': { 'value':'#{lastname}' }, 'role': { 'value': '#{role}' }, 'department': { 'value': '#{department}' }, 'college': { 'value': '#{college}' }, 'major': { 'value':'#{major}' }, } }, 'myberkeley': { 'access': 'principal', 'elements': { 'context': { 'value': '#{context}' }, 'standing': { 'value': '#{standing}' },'current': { 'value': '#{current}' }, 'major': { 'value': '#{major}' } } } }"
      return post_data
    end
      
    def update_profile_properties(sling, target_user, user_props)
      data = {}
      return target_user.update_properties(sling, add_profile_property(user_props, data))
    end
      
    def load_user(username, user_props, password=nil)
      target_user = create_user_with_props username, user_props, password
      
      # if user exists, they will not be (re)created but props may be updated
      if (target_user.nil?)
        puts "user #{username} not created, may already exist, attempting to update properties of user: #{user_props.inspect}"
        target_user = update_user(username, user_props, password)
      end

      # create users on the bedework server.
      # this will only work if the server has been put into unsecure login mode.
      if (@bedeworkServer)
        puts "Creating a bedework account for user #{username} on server #{@bedeworkServer}..."
        Net::HTTP.start(@bedeworkServer, @bedeworkPort) { |http|
          req = Net::HTTP::Options.new('/ucaldav/principals/users/' + username)
          req.basic_auth username, username
          response = http.request(req)
        }
      end

      return target_user
    end
  
    def update_user(username, user_props, password=nil)
      if (password)
          target_user = User.new username, password
        else
          target_user = User.new username
        end      
      update_profile_properties @sling, target_user, user_props
      return target_user
    end
  
    def apply_student_aces(student)
      home_path = student.home_path_for @sling
      @authz.delete(home_path, "everyone")
      @authz.grant(home_path,"everyone","jcr:read" => "denied","jcr:write" => "denied")
      @authz.delete(home_path, "anonymous")
      @authz.grant(home_path,"anonymous","jcr:read" => "denied","jcr:write" => "denied")
      @authz.grant(home_path,CED_ADVISORS_GROUP_NAME,"jcr:read" => "granted")
    end
    
    def apply_advisor_aces(advisor)
      home_path = advisor.home_path_for @sling
      @authz.delete(home_path, "everyone")
      @authz.grant(home_path,"everyone","jcr:read" => "denied","jcr:write" => "denied")
      @authz.delete(home_path, "anonymous")
      @authz.grant(home_path,"anonymous","jcr:read" => "denied","jcr:write" => "denied")
      @authz.grant(home_path,CED_ALL_STUDENTS_GROUP_NAME,"jcr:read" => "granted") #needed so message search results can include sender's profile info
      @authz.grant(home_path,CED_ADVISORS_GROUP_NAME,"jcr:all" => "granted")
    end

    def apply_student_demographic(student, index, length)
      isgrad = true
      if ( index < length/2)
          isgrad = false
      end
      standing = ""
      program = ""
      if ( isgrad )
        standing = "/colleges/CED/standings/grad"
        program = "/colleges/CED/standings/grad/programs/" + GRAD_MAJORS[index % UNDERGRAD_MAJORS.length].sub(/&/, 'AND')
      else
        standing = "/colleges/CED/standings/undergrad"
        program = "/colleges/CED/standings/undergrad/majors/" + UNDERGRAD_MAJORS[index % UNDERGRAD_MAJORS.length].sub(/&/, 'AND')
      end
      @sling.execute_post("#{@server}~#{student.name}.myb-demographic.html", "myb-demographics" => [ program, standing ] )
    end
  end
end

if ($PROGRAM_NAME.include? 'ucb_data_loader.rb')
  puts "will load data on server #{ARGV[0]}"
  sdl = MyBerkeleyData::UcbDataLoader.new ARGV[0], ARGV[1], ARGV[2]
  sdl.get_or_create_groups
  sdl.load_defined_user_advisors #now loading all the project members as advisors same as load_defined_users except adding to g-ced-advisors
  sdl.load_calnet_test_users
end