#!/usr/bin/env ruby

# TODO Need to bring up to date with dynamic lists.

# The Oracle server's host, port, and service name must be configured in:
#   $TNS_ADMIN/tnsnames.ora

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
require 'ruby-lib-dir.rb'

require 'rubygems'
require 'optparse'
require 'active_record'
require 'oci8'
require 'json'
require 'digest/sha1'
require 'logger'
require 'sling/sling'
require 'sling/users'
require 'ucb_data_loader'

module MyBerkeleyData
  # TODO Can we eliminate this translation to slim the integration?
  COLLEGE_ABBR_TO_DEMOGRAPHIC = {"ENV DSGN" => "CED"}
  COLLEGE_ABBR_TO_PROFILE = {'ENV DSGN' => 'College of Environmental Design'}
  class OracleDataLoader
    attr_reader :ucb_data_loader

    def initialize(options)
      @log = Logger.new(STDOUT)
      @log.level = Logger::DEBUG
      @oracle_user = options[:oracleuser]
      @oracle_password = options[:oraclepwd]
      @oracle_sid = options[:oraclesid]

      @additional_user_ids_file = options[:usersfile]
      @remaining_students = []

      @env = options[:runenv]
      @user_password_key = options[:userpwdkey]
      @sling = Sling.new(options[:appserver], true)
      real_admin = User.new("admin", options[:adminpwd])
      @sling.switch_user(real_admin)
      @sling.do_login
      @ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(options[:appserver], options[:adminpwd])

      ActiveRecord::Base.logger = Logger.new(STDOUT)
      #requires a TNSNAMES.ORA file and a TNS_ADMIN env variable pointing to directory containing it
      conn = ActiveRecord::Base.establish_connection(
          :adapter  => "oracle_enhanced",
          :username => @oracle_user,
          :password => @oracle_password,
          :database => @oracle_sid
         )
    end

    def collect_loaded_students
      @ucb_data_loader.get_or_create_groups
      @remaining_students = ucb_data_loader.get_all_student_uids
    end

    def make_user_props(student_row)
      user_props = {}
      user_props['firstName'] = student_row.first_name.strip
      user_props['lastName'] = student_row.last_name.strip
      user_props['email'] = make_email student_row
      determine_role user_props, student_row
      determine_current_status user_props, student_row
      determine_standing user_props, student_row
      determine_department user_props, student_row
      determine_college user_props, student_row
      determine_major user_props, student_row
      determine_demographics(user_props, student_row)
      return user_props
    end

    def determine_standing(user_props, student_row)
      if ('U'.eql?student_row.ug_grad_flag.strip )
        user_props['standing'] = 'undergrad'
      elsif ('G'.eql?student_row.ug_grad_flag.strip)
        user_props['standing'] = 'grad'
      else
        user_props['standing'] = 'unknown'
      end
    end

    def determine_major(user_props, student_row)
      profile_val = ''
      attributes_map = student_row.attributes()
      (1..4).each do |i|
        major_field = "major_name"
        major_title = "major_title"
        if (i > 1)
          major_field += i.to_s
          major_title += i.to_s
        end
        major_val = attributes_map[major_field].to_s.strip
        if (!major_val.empty?)
          major_title_val = attributes_map[major_title].to_s.strip
          major_val = major_title_val if (!major_title_val.empty?)
          if (i == 2)
            profile_val += " : "
          elsif (i > 2)
            profile_val += " ; "
          end
          profile_val += major_val
        end
      end
      user_props['major'] = profile_val
    end

    def determine_demographics(user_props, student_row)
      myb_demographics = []
      if (is_current_student(student_row))
        case student_row.ug_grad_flag.strip
        when 'G'
          standing_segment = '/standings/grad/programs/'
        when 'U'
          standing_segment = '/standings/undergrad/majors/'
        else
          @log.warn("#{student_row.student_ldap_uid} has unrecognized UG_GRAD_FLAG; no demographics")
        end
        if (!standing_segment.nil?)
          attributes_map = student_row.attributes()
          (1..4).each do |i|
            college_field = "college_abbr"
            major_field = "major_name"
            if (i > 1)
              college_field += i.to_s
              major_field += i.to_s
            end
            college_val = attributes_map[college_field].to_s.strip.sub(/&/, 'AND')
            if (COLLEGE_ABBR_TO_DEMOGRAPHIC[college_val])
              college_val = COLLEGE_ABBR_TO_DEMOGRAPHIC[college_val]
            end
            major_val = attributes_map[major_field].to_s.strip.sub(/&/, 'AND')
            if (!major_val.empty?)
              myb_demographics.push("/colleges/" + college_val)
              myb_demographics.push("/colleges/" + college_val + standing_segment + major_val)
            end
          end
          myb_demographics.uniq!
        end
      end
      @log.info("For student #{student_row.student_ldap_uid}, demographics = #{myb_demographics.inspect}")
      user_props["myb-demographics"] = myb_demographics
    end

    def is_current_student(student_row)
      return (student_row.affiliations.include? "STUDENT-TYPE-REGISTERED")
    end

    def determine_current_status(user_props, student_row)
      user_props['current'] = is_current_student(student_row)
    end

    def determine_role(user_props, student_row)
      user_props['role'] = UG_GRAD_FLAG_MAP[student_row.ug_grad_flag.to_sym]
    end

    def determine_department(user_props, student_row)
      user_props['department'] = '' # need the empty string for profile page trimpath template handling
    end

    def determine_college(user_props, student_row)
      profile_val = ''
      if (!student_row.college_abbr.nil?)
        profile_val = student_row.college_abbr.strip
        if (COLLEGE_ABBR_TO_PROFILE[profile_val])
          profile_val = COLLEGE_ABBR_TO_PROFILE[profile_val]
        end
      end
      user_props['college'] = profile_val
    end

    def make_email student_row
      if (ENV_PROD.eql?@env)
        email = student_row.student_email_address
      else #obfuscate the email
        email = "#{student_row.first_name.gsub(/\s*/,'').downcase}.#{student_row.last_name.gsub(/\s*/,'').downcase}@berkeley.edu"
      end
      return email
    end

    def select_ced_students
      ced_students =  MyBerkeleyData::Student.find_by_sql "select * from BSPACE_STUDENT_INFO_VW si
                left join BSPACE_STUDENT_MAJOR_VW sm on si.STUDENT_LDAP_UID = sm.LDAP_UID
                where (sm.COLLEGE_ABBR = 'ENV DSGN' or sm.COLLEGE_ABBR2 = 'ENV DSGN' or sm.COLLEGE_ABBR3 = 'ENV DSGN' or sm.COLLEGE_ABBR4 = 'ENV DSGN')
                and si.AFFILIATIONS like '%STUDENT-TYPE-REGISTERED%'"
      return ced_students
    end

    def load_single_student(user_id)
      # The ID must be numeric.
      if (/^([\d]+)$/ =~ user_id)
        studentdata = MyBerkeleyData::Student.find_by_sql "select * from BSPACE_STUDENT_INFO_VW si
                  left join BSPACE_STUDENT_MAJOR_VW sm on si.STUDENT_LDAP_UID = sm.LDAP_UID
                  where si.STUDENT_LDAP_UID = #{user_id}"
        if (!studentdata.empty?)
          load_student(studentdata[0])
        else
          @log.warn("Could not find DB record for user ID #{user_id} - RECONCILE MANUALLY")
        end
      else
        @log.warn("Could not find DB record for user ID #{user_id} - RECONCILE MANUALLY")
      end
    end

    def load_student(student_row)
      props = make_user_props(student_row)
      student_uid = student_row.student_ldap_uid.to_s
      if (props['current'] == true)
        if (@user_password_key)
          user_password = ucb_data_loader.make_password student_row.stu_name, @user_password_key
        else
          user_password = "testuser"
        end
        user = @ucb_data_loader.load_user student_uid, props, user_password
        @ucb_data_loader.add_student_to_group user
        @ucb_data_loader.apply_student_aces user
        @ucb_data_loader.apply_student_demographic student_uid, props
        if (@remaining_students.include?(student_uid))
          @remaining_students.delete(student_uid)
        end
      elsif (@remaining_students.include?(student_uid))
        @log.info("Removing non-current student #{student_uid}")
        drop_student(student_uid)
      end
    end

    def load_ced_students
      ced_students = select_ced_students
      @log.info("DB returned #{ced_students.length} target student records")
      i = 0
      ced_students.each do |s|
        load_student(s)
      end
    end

    def load_additional_students
      return if (@additional_user_ids_file.nil?)
      user_ids_file = File.open(@additional_user_ids_file, "r")
      additional_user_ids = user_ids_file.readlines
      user_ids_file.close
      @log.info("Additional students = #{additional_user_ids.inspect}")
      additional_user_ids.each do |user_id|
        user_id.chomp!
        load_single_student(user_id)
      end
    end
    
    def drop_stale_students
      @log.info("Remaining students = #{@remaining_students.inspect}")
      iterating_copy = Array.new(@remaining_students)
      iterating_copy.each do |student_uid|
        drop_student(student_uid)
      end
    end
    
    def drop_student(student_uid)
      # Delete demographic.
      user_props = {"myb-demographics"  => []}
      @ucb_data_loader.apply_student_demographic student_uid, user_props
      # TODO Change user's ACL?
      @ucb_data_loader.remove_student_from_group(student_uid)
      @remaining_students.delete(student_uid)
    end
  end

  class Student < ActiveRecord::Base
    set_table_name "BSPACE_STUDENT_INFO_VW"
    set_primary_key "student_ldap_uid"
  end
end

if ($PROGRAM_NAME.include? 'oracle_data_loader.rb')

  options = {}
  optparser = OptionParser.new do |opts|
    opts.banner = "Usage: oracle_data_loader.rb [options]"

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

    opts.on("-k", "--userpwdkey USERPWDKEY", "Key used to encrypt user passwords") do |pk|
      options[:userpwdkey] = pk
    end

    opts.on("-f", "--fileids USERIDS", "File of user_ids") do |nu|
      options[:usersfile] = nu
    end
  end

  optparser.parse ARGV

  odl = MyBerkeleyData::OracleDataLoader.new options

  odl.collect_loaded_students
  odl.ucb_data_loader.load_defined_advisors
  odl.load_ced_students
  odl.load_additional_students
  odl.drop_stale_students

end