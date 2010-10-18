#!/usr/bin/env ruby
require 'rubygems'
require 'optparse'
require 'active_record'
require 'json'
require 'digest/sha1'
require 'sling/sling'
require 'lib/sling/users'
require 'sling_data_loader'
include SlingInterface
include SlingUsers

module MyBerkeleyData
  
  class OracleDataLoader
    CED_ADVISORS_GROUP_NAME = "g-ced-advisors"
    CED_ALL_STUDENTS_GROUP_NAME = "g-ced-students"
    MAJORS = ["ARCHITECTURE", "CITY REGIONAL PLAN", "DESIGN", "LIMITED", "LANDSCAPE ARCH", "LAND ARCH & ENV PLAN", "URBAN DESIGN", "URBAN STUDIES"]
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
    
    attr_reader :oracle_host, :oracle_user, :oracle_password, :oracle_sid, :user_password_key, :num_students, :ced_advisors_group, :ced_all_students_group
    
    def initialize(opts)
      @oracle_host = opts[:oraclehost]
      @oracle_user = opts[:oracleuser]
      @oracle_password = opts[:oraclepwd]
      @oracle_sid = opts[:oraclesid]
      
      @env = opts[:runenv]
      @user_password_key = opts[:userpwdkey]
      @num_students = opts[:numstudents]
      @sling = Sling.new(opts[:appserver], opts[:adminpwd], true)
      @sling.do_login
      @user_manager = UserManager.new(@sling)
    end
    
    def get_or_create_groups
      @ced_advisors_group = get_or_create_group CED_ADVISORS_GROUP_NAME
      @ced_all_students_group = get_or_create_group CED_ALL_STUDENTS_GROUP_NAME
    end
    
    #def get_or_create_group(groupname)
    #  if (!@user_manager.group_exists? groupname)
    #    group = @user_manager.create_group groupname
    #  else
    #    group = Group.new groupname
    #  end
    #  return group
    #end
    #
    
    def get_or_create_group(groupname)
      group = @user_manager.create_group groupname
      if(!group)
        group = Group.new groupname
      end
      return group
    end
    
    
    def make_user_props(ced_student)
      user_props = {}
      user_props['firstName'] = ced_student.first_name
      user_props['lastName'] = ced_student.last_name
      user_props['email'] = make_email ced_student
      user_props['context'] = ['g-ced-students']
      user_props['participant'] = true
      if (is_current ced_student )
        user_props['current'] = true;
      else
        user_props['current'] = false
      end
      if ('U'.eql?ced_student.ug_grad_flag.strip )
        user_props['standing'] = 'undergrad'
      elsif ('G'.eql?ced_student.ug_grad_flag.strip)
        user_props['standing'] = 'grad'
      else
        user_props['standing'] = 'unknown'
      end
      determine_major user_props, ced_student
      return user_props
    end
    
    def determine_major(user_props, ced_student)
      if ('DOUBLE'.eql?ced_student.major_name.strip)
        if ('ENV DSGN'.eql?ced_student.college_abbr2.strip)
          user_props['major'] = [ced_student.major_name2.strip]
        end
        if (('ENV DSGN'.eql?ced_student.college_abbr3.strip))
          user_props['major'] = [ced_student.major_name3.strip]
        end
      else
        user_props['major'] = [ced_student.major_name.strip]
      end
    end
    
    # STUDENT-TYPE-REGISTERED,EMPLOYEE-STATUS-EXPIRED
    def is_current(ced_student)
      if (ced_student.affiliations.include? "STUDENT-TYPE-REGISTERED")
        return true
      else
        return false
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
      digest.update(ced_student.stu_name).update(@user_password_key)
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
        loadedAdvisor = sdl.load_user username, user_props
        add_advisor_to_group loadedAdvisor
      end      
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
  
  options = {}
  optparse = OptionParser.new do |opts|
    opts.banner = "Usage: oracle_data_loader.rb [options]"

    opts.on("-h", "--oraclehost OHOST", "Oracle Host") do |oh|
      options[:oraclehost] = oh
    end
    
    opts.on("-u", "--oracleuser OUSER", "Oracle User") do |ou|
      options[:oracleuser] = ou
    end
    
    opts.on("-p", "--oraclepwd OPWD", "Oracle Password") do |op|
      options[:oraclepwd] = op
    end
    
    opts.on("-i", "--oraclesid OSID", "Oracle SID") do |oi|
      options[:oraclesid] = oi
    end
    
    # trailing slash is mandatory
    options[:appserver] = "http://localhost:8080/" 
    opts.on("-a", "--appserver [APPSERVE]", "Application Server") do |as|
      options[:appserver] = as
    end
    
    options[:adminpwd] = "admin"
    opts.on("-q", "--adminpwd [ADMINPWD]", "Application Admin User Password") do |ap|
      options[:adminpwd] = ap
    end
    
    options[:runenv] = "dev"
    opts.on("-e", "--runenv [RUNENV]", "Runtime Environment") do |re|
      options[:runenv] = re
    end
    
    options[:numstudents] = 10
    opts.on("-n", "--numstudents [NUMSTUDENTS]", Integer, "Number of Students to load") do |ns|
      options[:numstudents] = ns
    end  

    opts.on("-k", "--userpwdkey USERPWDKEY", "Key used to encrypt user passwords") do |pk|
      options[:userpwdkey] = pk
    end      
  end
  
  optparse.parse ARGV
  
  odl = MyBerkeleyData::OracleDataLoader.new options
  #initialize(oracle_host, oracle_user, oracle_password, oracle_sid, target_server="http://localhost:8080", env="dev")
  #odl = MyBerkeleyData::OracleDataLoader.new ARGV[0], ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5]
  odl.get_or_create_groups
    
  ActiveRecord::Base.logger = Logger.new(STDOUT)
  conn = ActiveRecord::Base.establish_connection(
      :adapter  => "oracle_enhanced",
      :host     => odl.oracle_host,
      :username => odl.oracle_user,
      :password => odl.oracle_password,
      :database => odl.oracle_sid
     )

  sdl = SlingDataLoader.new(options[:appserver], options[:adminpwd], 0) # no random users, just real ones
  #sdl.load_advisors
  #advisors = sdl.load_defined_users "json_data.js"
  
  #ced_students =  MyBerkeleyData::Student.find_by_sql "select * from BSPACE_STUDENT_INFO_VW si where si.STUDENT_LDAP_UID in \
                  #(select sm.LDAP_UID from BSPACE_STUDENT_MAJOR_VW sm where sm.COLLEGE_ABBR = 'ENV DSGN' or sm.COLLEGE_ABBR2 = 'ENV DSGN' \
                  #or sm.COLLEGE_ABBR3 = 'ENV DSGN' or sm.COLLEGE_ABBR4 = 'ENV DSGN')"
  
  #ced_students = MyBerkeleyData::Student.find :all, :conditions => {}
  
  ced_students =  MyBerkeleyData::Student.find_by_sql "select * from BSPACE_STUDENT_INFO_VW si
                  left join BSPACE_STUDENT_MAJOR_VW sm on si.STUDENT_LDAP_UID = sm.LDAP_UID
                  where sm.COLLEGE_ABBR = 'ENV DSGN' or sm.COLLEGE_ABBR2 = 'ENV DSGN' or sm.COLLEGE_ABBR3 = 'ENV DSGN' or sm.COLLEGE_ABBR4 = 'ENV DSGN'"
  i = 0
  ced_students.each do |s|
    break if (i += 1) == odl.num_students
    props = odl.make_user_props s
    if (odl.user_password_key)
      user_password = odl.make_password(s)
    else
      user_password = "testuser"
    end
    user = sdl.load_user s.student_ldap_uid, props, user_password
    odl.add_student_to_group user
  end
end 