#!/usr/bin/env ruby
require 'rubygems'
require 'active_record'
require 'json'
require 'digest/sha1'
require 'lib/sling/sling'
require 'lib/sling/users'
require 'sling_data_loader'
include SlingInterface
include SlingUsers

module MyBerkeleyData
  
  class OracleDataLoader
    CED_ADVISORS_GROUP_NAME = "g-ced-advisors"
    CED_ALL_STUDENTS_GROUP_NAME = "g-ced-students"
    ENV_PROD = 'prod'

    @env = nil
    @ced_advisors_group = nil
    @ced_all_students_group = nil
    
    @user_manager = nil
    @sling = nil
    
    @oracle_host = nil
    @oracle_user = nil
    @oracle_password = nil
    @oracle_sid = nil
    
    attr_reader :oracle_host, :oracle_user, :oracle_password, :oracle_sid, :ced_advisors_group, :ced_all_students_group
    
    def initialize(oracle_host, oracle_user, oracle_password, oracle_sid, target_server="http://localhost:8080", env="dev")
      @oracle_host = oracle_host
      @oracle_user = oracle_user
      @oracle_password = oracle_password
      @oracle_sid = oracle_sid
      
      @env = env
      
      @sling = Sling.new(target_server, true)
      @sling.do_login
      @user_manager = UserManager.new(@sling)
    end
    
    def get_or_create_groups
      @ced_advisors_group = get_or_create_group CED_ADVISORS_GROUP_NAME
      @ced_all_students_group = get_or_create_group CED_ALL_STUDENTS_GROUP_NAME
    end
    
    def get_or_create_group(groupname)
      if (!@user_manager.group_exists? groupname)
        group = @user_manager.create_group groupname
      else
        group = Group.new groupname
      end
      return group
    end
    
    
    def make_user_props(ced_student)
      user_props = {}
      user_props['firstName'] = ced_student.first_name
      user_props['lastName'] = ced_student.last_name
      user_props['email'] = make_email ced_student
      if ('U'.eql?ced_student.ug_grad_flag.strip )
        user_props['sakai:standing'] = 'undergrad'
      elsif ('G'.eql?ced_student.ug_grad_flag.strip)
        user_props['sakai:standing'] = 'grad'
      else
        user_props['sakai:standing'] = 'unknown'
      end
      determine_major user_props, ced_student
      return user_props
    end
    
    def determine_major(user_props, ced_student)
      if ('DOUBLE'.eql?ced_student.major_name.strip)
        if ('ENV DSGN'.eql?ced_student.college_abbr2.strip)
          user_props['major'] = ced_student.major_name2.strip
        end
        if (('ENV DSGN'.eql?ced_student.college_abbr3.strip))
          if (user_props['major'])
            user_props['major2'] = ced_student.major_name3.strip
          else
            user_props['major'] = ced_student.major_name3.strip
          end
        end
      else
        user_props['major'] = ced_student.major_name.strip
      end
    end
    
    def make_email ced_student
      if (ENV_PROD.eql?@env) 
        email = ced_student.student_email_address
      else #obfuscate the email
        email = "#{ced_student.first_name.gsub(/\s*/,'').downcase}.#{ced_student.last_name.gsub(/\s*/,'').downcase}@berkeley.edu"
      end
      return email
    end
    
    
    def make_password ced_student
      digest = Digest::SHA1.new
      digest.update(ced_student.stu_name).update("myberkeleykey")
      return digest.hexdigest
    end
    
    def add_student_to_group user
      @ced_all_students_group.add_member @sling, user, "user"
      user_props = @user_manager.get_user_props user.name
    end
    
    def add_advisor_to_group advisor
      @ced_advisors_group.add_member @sling, advisor, "user"
      user_props = @user_manager.get_user_props advisor.name
    end
    
  end
  

 
# STUDENT_LDAP_UID
#UG_GRAD_FLAG
#ROLE_CD
#STU_NAME
#STUDENT_EMAIL_ADDRESS
#EMAIL_DISCLOS_CD
#EDUC_LEVEL
#FIRST_NAME
#LAST_NAME
#STUDENT_ID
#AFFILIATIONS

  class Major < ActiveRecord::Base
    set_table_name "BSPACE_STUDENT_MAJOR_VW"
    set_primary_key "ldap_uid"
    belongs_to :student, :foreign_key => "student_ldap_uid"
  end
  
  class Student < ActiveRecord::Base
    set_table_name "BSPACE_STUDENT_INFO_VW"
    set_primary_key "student_ldap_uid"
  end
end

if ($PROGRAM_NAME.include? 'oracle_data_loader.rb')
  #initialize(oracle_host, oracle_user, oracle_password, oracle_sid, target_server="http://localhost:8080", env="dev")
  odl = MyBerkeleyData::OracleDataLoader.new ARGV[0], ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5]
  odl.get_or_create_groups
    
  ActiveRecord::Base.logger = Logger.new(STDOUT)
  conn = ActiveRecord::Base.establish_connection(
      :adapter  => "oracle_enhanced",
      :host     => odl.oracle_host,
      :username => odl.oracle_user,
      :password => odl.oracle_password,
      :database => odl.oracle_sid
     )

  sdl = SlingDataLoader.new(ARGV[4], 0) # no random users, just real ones
  #advisors = sdl.load_defined_users "json_data.js"
    all_data = JSON.load(File.open "json_data.js", "r")
    advisors = all_data['users']
    advisors.each do |advisor|
      username = advisor[0]
      user_props = advisor[1]
      puts "creating advisor: #{advisor.inspect}"
      loadedAdvisor = sdl.load_user username, user_props
      odl.add_advisor_to_group loadedAdvisor
    end

  
  #ced_students =  MyBerkeleyData::Student.find_by_sql "select * from BSPACE_STUDENT_INFO_VW si where si.STUDENT_LDAP_UID in \
  #                (select sm.LDAP_UID from BSPACE_STUDENT_MAJOR_VW sm where sm.COLLEGE_ABBR = 'ENV DSGN' or sm.COLLEGE_ABBR2 = 'ENV DSGN' \
  #                or sm.COLLEGE_ABBR3 = 'ENV DSGN' or sm.COLLEGE_ABBR4 = 'ENV DSGN')"
  
  #ced_students = MyBerkeleyData::Student.find :all, :conditions => {}
  
  #ced_students =  MyBerkeleyData::Student.find_by_sql "select * from BSPACE_STUDENT_INFO_VW si
  #                left join BSPACE_STUDENT_MAJOR_VW sm on si.STUDENT_LDAP_UID = sm.LDAP_UID
  #                where sm.MAJOR_NAME = 'DOUBLE' and (sm.COLLEGE_ABBR2 = 'ENV DSGN' or sm.COLLEGE_ABBR3 = 'ENV DSGN' or sm.COLLEGE_ABBR4 = 'ENV DSGN')"
  #
  #i = 0
  #ced_students.each do |s|
  #  #break if (i += 1) == 10
  #  props = odl.make_user_props s
  #  user = sdl.load_user s.student_ldap_uid, props, odl.make_password(s) 
  #  odl.add_student_to_group user
  #end
end 