#!/usr/bin/env ruby

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
  COLLEGES = "'ENV DSGN', 'NAT RES'"
  class OracleDataLoader
    attr_reader :ucb_data_loader

    def initialize(options)
      @log = Logger.new(STDOUT)
      @log.level = Logger::DEBUG
      @oracle_user = options[:oracleuser]
      @oracle_password = options[:oraclepwd]
      @oracle_sid = options[:oraclesid]

      @remaining_accounts = []

      @env = options[:runenv]
      @user_password_key = options[:userpwdkey]
      @sling = Sling.new(options[:appserver], true)
      real_admin = User.new("admin", options[:adminpwd])
      @sling.switch_user(real_admin)
      @sling.do_login
      @ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(options[:appserver], options[:adminpwd])

      @bedeworkServer = options[:bedeworkServer]
      @bedeworkPort = options[:bedeworkPort]

      ActiveRecord::Base.logger = Logger.new(STDOUT)
      #requires a TNSNAMES.ORA file and a TNS_ADMIN env variable pointing to directory containing it
      conn = ActiveRecord::Base.establish_connection(
          :adapter  => "oracle_enhanced",
          :username => @oracle_user,
          :password => @oracle_password,
          :database => @oracle_sid
         )
    end

    def collect_integrated_accounts
      @remaining_accounts = ucb_data_loader.get_all_ucb_accounts
    end

    def make_user_props(person_row)
      user_props = {}
      user_props['firstName'] = person_row.first_name.strip
      user_props['lastName'] = person_row.last_name.strip
      user_props['email'] = person_row.email_address
      determine_role user_props, person_row
      determine_college user_props, person_row
      determine_major user_props, person_row
      determine_demographics(user_props, person_row)
      return user_props
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
      if (!profile_val.empty?)
        user_props['major'] = profile_val
      end
    end

    def determine_demographics(user_props, person_row)
      myb_demographics = []
      if (is_current_student(person_row))
        if (!person_row.ug_grad_flag.nil?)
          case person_row.ug_grad_flag.strip
          when 'G'
            standing_val = '/standings/grad'
            major_segment = '/standings/grad/majors/'
          when 'U'
            standing_val = '/standings/undergrad'
            major_segment = '/standings/undergrad/majors/'
          end
        end
        if (!person_row.level_desc_s.nil?)
          myb_demographics.push("/student/educ_level/#{person_row.level_desc_s.strip}")
        end
        if (!person_row.new_trfr_flag.nil?)
          myb_demographics.push("/student/new_trfr_flag/#{person_row.new_trfr_flag.strip}")
        end
        if (standing_val.nil?)
          @log.warn("Current student #{person_row.ldap_uid.to_i.to_s} has unrecognized UG_GRAD_FLAG #{person_row.ug_grad_flag}; no demographics")
        else
          attributes_map = person_row.attributes()
          (1..4).each do |i|
            college_field = "college_abbr"
            major_field = "major_name"
            if (i > 1)
              college_field += i.to_s
              major_field += i.to_s
            end
            college_val = attributes_map[college_field].to_s.strip
            major_val = attributes_map[major_field].to_s.strip
            if (!major_val.empty?)
              myb_demographics.push("/colleges/" + college_val + standing_val)
              myb_demographics.push("/colleges/" + college_val + major_segment + major_val)
            end
          end
          myb_demographics.uniq!
        end
      end
      @log.info("For person #{person_row.ldap_uid.to_i.to_s}, demographics = #{myb_demographics.inspect}")
      user_props["myb-demographics"] = myb_demographics
    end

    def is_current_student(person_row)
      return (person_row.affiliations.include? "STUDENT-TYPE-REGISTERED")
    end

    # Modeled after bSpace's EduAffliationsMapper
    def determine_role(user_props, person_row)
      user_props['role'] = if !person_row.ug_grad_flag.nil?
        UG_GRAD_FLAG_MAP[person_row.ug_grad_flag.strip.to_sym]
      elsif person_row.affiliations.include?("STUDENT-TYPE-REGISTERED")
        "Student" # There are 730 of these in the DB
      elsif person_row.affiliations.include?("EMPLOYEE-TYPE-ACADEMIC")
        "Instructor"
      elsif person_row.affiliations.include?("EMPLOYEE-TYPE-STAFF")
        "Staff"
      elsif person_row.affiliations.include?("AFFILIATE-TYPE-VISITING")
        "Instructor"
      else
        "Guest"
      end
    end

    def determine_college(user_props, student_row)
      if (!student_row.college_abbr.nil?)
        profile_val = student_row.college_abbr.strip
        if (COLLEGE_ABBR_TO_PROFILE[profile_val])
          profile_val = COLLEGE_ABBR_TO_PROFILE[profile_val]
        end
      end
      if (!profile_val.nil?)
        user_props['college'] = profile_val
      end
    end

    def select_students_from_colleges(colleges)
      student_rows =  MyBerkeleyData::Student.find_by_sql(
        "select pi.STUDENT_LDAP_UID as LDAP_UID, pi.UG_GRAD_FLAG, pi.FIRST_NAME, pi.LAST_NAME,
           pi.STUDENT_EMAIL_ADDRESS as EMAIL_ADDRESS, pi.STU_NAME as PERSON_NAME, pi.AFFILIATIONS,
           sm.MAJOR_NAME, sm.MAJOR_TITLE, sm.COLLEGE_ABBR, sm.MAJOR_NAME2, sm.MAJOR_TITLE2, sm.COLLEGE_ABBR2,
           sm.MAJOR_NAME3, sm.MAJOR_TITLE3, sm.COLLEGE_ABBR3, sm.MAJOR_NAME4, sm.MAJOR_TITLE4, sm.COLLEGE_ABBR4,
           sm.MAJOR_CD, sm.MAJOR_CD2, sm.MAJOR_CD3, sm.MAJOR_CD4,
           sp.LEVEL_DESC_S, st.NEW_TRFR_FLAG
           from BSPACE_STUDENT_MAJOR_VW sm join BSPACE_STUDENT_INFO_VW pi on pi.STUDENT_LDAP_UID = sm.LDAP_UID
           left join BSPACE_STUDENT_PORTAL_VW sp on pi.STUDENT_LDAP_UID = sp.LDAP_UID
           left join BSPACE_STUDENT_TERM_VW st on pi.STUDENT_LDAP_UID = st.LDAP_UID
           where (sm.COLLEGE_ABBR in (#{colleges}) or sm.COLLEGE_ABBR2 in (#{colleges}) or
             sm.COLLEGE_ABBR3 in (#{colleges}) or sm.COLLEGE_ABBR4  in (#{colleges}))
           and pi.AFFILIATIONS like '%STUDENT-TYPE-REGISTERED%'"
      )
      return student_rows
    end

    def load_single_account(user_id)
      # The ID must be numeric.
      if (/^([\d]+)$/ =~ user_id)
        persondata = MyBerkeleyData::Person.find_by_sql(
        "select pi.LDAP_UID, pi.UG_GRAD_FLAG, pi.FIRST_NAME, pi.LAST_NAME,
           pi.EMAIL_ADDRESS, pi.PERSON_NAME, pi.AFFILIATIONS,
           sm.MAJOR_NAME, sm.MAJOR_TITLE, sm.COLLEGE_ABBR, sm.MAJOR_NAME2, sm.MAJOR_TITLE2, sm.COLLEGE_ABBR2,
           sm.MAJOR_NAME3, sm.MAJOR_TITLE3, sm.COLLEGE_ABBR3, sm.MAJOR_NAME4, sm.MAJOR_TITLE4, sm.COLLEGE_ABBR4,
           sm.MAJOR_CD, sm.MAJOR_CD2, sm.MAJOR_CD3, sm.MAJOR_CD4,
           sp.LEVEL_DESC_S, st.NEW_TRFR_FLAG
           from BSPACE_PERSON_INFO_VW pi
           left join BSPACE_STUDENT_MAJOR_VW sm on pi.LDAP_UID = sm.LDAP_UID
           left join BSPACE_STUDENT_PORTAL_VW sp on pi.LDAP_UID = sp.LDAP_UID
           left join BSPACE_STUDENT_TERM_VW st on pi.LDAP_UID = st.LDAP_UID
           where pi.LDAP_UID = #{user_id}"
        )
        if (!persondata.empty?)
          load_user_from_row(persondata[0])
        else
          @log.warn("Could not find DB record for user ID #{user_id} - RECONCILE MANUALLY")
        end
      else
        @log.warn("Could not find DB record for user ID #{user_id} - RECONCILE MANUALLY")
      end
    end

    def load_user_from_row(person_row, fake_email=false)
      props = make_user_props(person_row)
      if (fake_email)
        props['email'] = "#{props['firstName'].gsub(/\s*/,'').downcase}.#{props['lastName'].gsub(/\s*/,'').downcase}@example.edu"
      end
      person_uid = person_row.ldap_uid.to_i.to_s
      create_bedework_acct(person_uid)
      if (@user_password_key)
        user_password = ucb_data_loader.make_password person_row.person_name, @user_password_key
      else
        user_password = "testuser"
      end
      user = @ucb_data_loader.load_user person_uid, props, user_password
      if (@remaining_accounts.include?(person_uid))
        @remaining_accounts.delete(person_uid)
      end
      user
    end

    def load_all_students
      fake_email = !(ENV_PROD.eql?@env)
      students = select_students_from_colleges(COLLEGES)
      @log.info("DB returned #{students.length} student records for colleges #{COLLEGES}")
      students.each do |s|
        load_user_from_row(s, fake_email)
      end
    end

    def drop_stale_accounts
      @log.info("Remaining accounts = #{@remaining_accounts.inspect}")
      iterating_copy = Array.new(@remaining_accounts)
      iterating_copy.each do |account_uid|
        drop_account(account_uid)
      end
    end

    def drop_account(account_uid)
      # Delete demographic.
      user_props = {"myb-demographics"  => []}
      @ucb_data_loader.apply_demographic account_uid, user_props
      
      # Delete obsolete profile sections.
      res = @sling.execute_post(@sling.url_for("~#{account_uid}/public/authprofile/email.profile.json"), {
        ":operation" => "import",
        ":contentType" => "json",
        ":removeTree" => "true",
        ":content" => '{"elements":{}}'
      })
      res = @sling.execute_post(@sling.url_for("~#{account_uid}/public/authprofile/institutional.profile.json"), {
        ":operation" => "import",
        ":contentType" => "json",
        ":removeTree" => "true",
        ":content" => '{"elements":{}}'
      })
      
      @remaining_accounts.delete(account_uid)
    end

    def load_additional_accounts
      file_data = JSON.load(File.open("additional_ucb_users_json.js", "r"))
      users_data = file_data["users"]
      users_data.each do |user_data|
        @log.info("user_data = #{user_data.inspect}")
        user_id = user_data[0]
        user_props = user_data[1]
        loaded_user = load_single_account(user_id)
        if (!loaded_user.nil?)
          groups = user_props["groups"]
          if (!groups.nil?)
            groups.each do |group_id|
              @ucb_data_loader.add_user_to_group(user_id, group_id)
            end
          end
          contexts = user_props["contexts"]
          if (!contexts.nil?)
            contexts.each do |context_id|
              @ucb_data_loader.add_reader_to_context(user_id, context_id)
            end
          end
          demographics = user_props["demographics"]
          if (!demographics.nil?)
            # WARNING: This will wipe out any existing campus demographics.
            @ucb_data_loader.apply_demographic(user_id, {"myb-demographics" => demographics})
          end
        end
      end
    end

    def create_bedework_acct(username)
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
    end

  end

  class Student < ActiveRecord::Base
    set_table_name "BSPACE_STUDENT_INFO_VW"
    set_primary_key "student_ldap_uid"
  end
  class Person < ActiveRecord::Base
    set_table_name "BSPACE_PERSON_INFO_VW"
    set_primary_key "ldap_uid"
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

    options[:bedeworkServer] = nil
    opts.on("--bedeworkserver [BEDEWORKSERVER]", "Bedework server") do |bs|
      options[:bedeworkServer] = bs
    end

    options[:bedeworkPort] = 8080
    opts.on("--bedeworkport [BEDEWORKPORT]", "Bedework server port") do |bp|
      options[:bedeworkPort] = bp
    end

  end

  optparser.parse ARGV

  odl = MyBerkeleyData::OracleDataLoader.new options

  odl.collect_integrated_accounts
  odl.load_all_students
  odl.load_additional_accounts
  odl.drop_stale_accounts

end