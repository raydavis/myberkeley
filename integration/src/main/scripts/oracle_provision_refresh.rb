#!/usr/bin/env ruby

# This transitional Ruby script uses server-side provision to refresh user
# accounts, but still uses a script-based DB query to get the list of
# student accounts that need refreshing.

# The Oracle server's host, port, and service name must be configured in:
#   $TNS_ADMIN/tnsnames.ora

require 'rubygems'
require 'optparse'
require 'active_record'
require 'oci8'
require 'json'
require 'digest/sha1'
require 'logger'
require 'nakamura'
require 'nakamura/users'
require_relative 'ucb_data_loader'

## Block sling.rb's monkeying with form values.
module Net::HTTPHeader
  def encode_kvpair(k, vs)
    if vs.nil? or vs == '' then
      "#{urlencode(k)}="
    elsif vs.kind_of?(Array)
      # In Ruby 1.8.7, Array(string-with-newlines) will split the string
      # after each embedded newline.
      Array(vs).map {|v| "#{urlencode(k)}=#{urlencode(v.to_s)}" }
    else
      "#{urlencode(k)}=#{urlencode(vs.to_s)}"
    end
  end
end

module MyBerkeleyData
  COLLEGES = "'ENV DSGN', 'NAT RES'"
  CALCENTRAL_TEAM_GROUP = "CalCentral-Team"
  class OracleDataLoader
    attr_reader :ucb_data_loader

    def initialize(options)
      @log = Logger.new(STDOUT)
      @log.level = Logger::DEBUG
      @oracle_user = options[:oracleuser]
      @oracle_password = options[:oraclepwd]
      @oracle_sid = options[:oraclesid]

      @remaining_accounts = []
      @participants = []
      @new_users = []
      @dropped_accounts = []
      @renewed_accounts = []
      @synchronized_accounts = []

      @env = options[:runenv]
      @ucb_data_loader = MyBerkeleyData::UcbDataLoader.new(options[:appserver], options[:adminpwd])
      @sling = @ucb_data_loader.sling

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
      ucbAccounts = ucb_data_loader.get_all_ucb_accounts
      @participants = ucbAccounts['participants']
      @remaining_accounts = ucbAccounts['nonparticipants'] | @participants
    end

    def select_students_from_colleges(colleges)
      student_rows =  MyBerkeleyData::Student.find_by_sql(
        "select pi.STUDENT_LDAP_UID as LDAP_UID
           from BSPACE_STUDENT_MAJOR_VW sm join BSPACE_STUDENT_INFO_VW pi on pi.STUDENT_LDAP_UID = sm.LDAP_UID
           left join BSPACE_STUDENT_PORTAL_VW sp on pi.STUDENT_LDAP_UID = sp.LDAP_UID
           left join BSPACE_STUDENT_TERM_VW st on pi.STUDENT_LDAP_UID = st.LDAP_UID
           where (sm.COLLEGE_ABBR in (#{colleges}) or sm.COLLEGE_ABBR2 in (#{colleges}) or
             sm.COLLEGE_ABBR3 in (#{colleges}) or sm.COLLEGE_ABBR4  in (#{colleges}))
           and pi.AFFILIATIONS like '%STUDENT-TYPE-REGISTERED%'"
      )
      return student_rows
    end

    def load_single_account(user_id, preset_props={})
      load_user_from_uid(user_id, false, preset_props)
    end

  def load_user_from_uid(person_uid, fake_email=false, preset_props={})
    props = preset_props.clone
    if (fake_email)
      props['email'] = "#{person_uid}@example.edu"
    end
    res = @sling.execute_post(@sling.url_for("system/myberkeley/personProvision"), {
      "userIds" => person_uid
    })
    @log.info("provision returned #{res.code}, #{res.body}")
    if (res.code.to_i > 299)
      @log.error("Could not load user #{person_uid}: #{res.code}, #{res.body}")
      return
    end
    json = JSON.parse(res.body)
    provision_result = json["results"][0]
    if (provision_result["synchronizationState"] == "error")
      return nil, false
    end
    new_user = (provision_result["synchronizationState"] == "created")
    # set additonal props if any
    if (props.length > 0)
      props["userId"] = person_uid
      res = @sling.execute_post(@sling.url_for("system/myberkeley/testPersonProvision"), props)
      if (res.code.to_i > 299)
        @log.error("Could not load added props for user #{person_uid}: #{res.code}, #{res.body}")
        return
      end
    end
    if (@remaining_accounts.include?(person_uid))
      @synchronized_accounts.push(person_uid)
      @remaining_accounts.delete(person_uid)
    else
      if (new_user)
        @new_users.push(person_uid)
      else
        @renewed_accounts.push(person_uid)
      end
    end
    new_user
  end

    def load_all_students
      fake_email = !(ENV_PROD.eql?@env)
      students = select_students_from_colleges(COLLEGES)
      @log.info("DB returned #{students.length} student records for colleges #{COLLEGES}")
      students.each do |person_row|
        person_uid = person_row.ldap_uid.to_i.to_s
        load_user_from_uid(person_uid, fake_email)
      end
    end

    def drop_stale_accounts
      iterating_copy = Array.new(@remaining_accounts)
      iterating_copy.each do |account_uid|
        drop_account(account_uid)
      end
    end

    def drop_account(account_uid)
      # Delete demographic.
      res = @sling.execute_post(@sling.url_for("~#{account_uid}.myb-demographic.html"), {
        "myb-demographics@Delete" => ""
      })

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

      @dropped_accounts.push(account_uid)
      @remaining_accounts.delete(account_uid)
    end

    def load_additional_accounts
      file_data = JSON.load(File.open("additional_ucb_users_json.js", "r"))
      users_data = file_data["users"]
      users_data.each do |user_data|
        @log.info("user_data = #{user_data.inspect}")
        user_id = user_data[0]
        user_extras = user_data[1]
        predefined_props = {
        	"myb-demographics" => user_extras["demographics"]
        }
        loaded_user = load_single_account(user_id, predefined_props)
        if (!loaded_user.nil?)
          groups = user_extras["groups"]
          if (!groups.nil?)
            groups.each do |group_id|
              @ucb_data_loader.add_user_to_group(user_id, group_id)
            end
          end
        end
      end
    end

    def check_stale_accounts
      iterating_copy = Array.new(@remaining_accounts)
      iterating_copy.each do |account_uid|
        if (@participants.include?(account_uid))
          @log.info("Refreshing old participant #{account_uid}")
          loaded_user = load_single_account(account_uid)
          @remaining_accounts.delete(account_uid)
        end
      end
    end

    def get_student_count(dynamicListContext, collegeId)
      res = @sling.execute_get(@sling.url_for("var/myberkeley/dynamiclists/#{dynamicListContext}.json"), {
        "criteria" => "{ANY:['/colleges/#{collegeId}/standings/grad','/colleges/#{collegeId}/standings/undergrad']}"
      })
      if (res.code == "200")
        return (JSON.parse(res.body))["count"]
      else
        @log.warn("Not able to check number of students: #{res.code}, #{res.body}")
        return "Unknown"
      end
   end

    def report_activity
      @log.info("Synchronized #{@synchronized_accounts.length} existing accounts")
      @log.info("Added #{@new_users.length} new users: #{@new_users.inspect}")
      @log.info("Renewed #{@renewed_accounts.length} synchronizations: #{@renewed_accounts.inspect}")
      @log.info("Dropped #{@dropped_accounts.length} synchronizations: #{@dropped_accounts.inspect}")
      @log.info("Currently #{@participants.length} participants: #{@participants.inspect}")
      # Wait for indexing.
      sleep(4)
      cedStudentCount = get_student_count("myb-ced-students", 'ENV DSGN')
      cnrStudentCount = get_student_count("myb-cnr-students", 'NAT RES')
      @log.info("#{cedStudentCount} CED Students")
      @log.info("#{cnrStudentCount} CNR Students")
      res = @sling.execute_get(@sling.url_for("~#{CALCENTRAL_TEAM_GROUP}.json"))
      if (res.code == "200")
        messagebody = "* Synchronized #{@synchronized_accounts.length} existing accounts\n" +
          "* Added #{@new_users.length} new users\n" +
          "* Renewed #{@renewed_accounts.length} synchronizations\n" +
          "* Dropped #{@dropped_accounts.length} synchronizations\n" +
          "* #{cedStudentCount} CED Students\n" +
          "* #{cnrStudentCount} CNR Students\n" +
          "* #{@participants.length} participants\n"
        subjectline = Time.now.strftime("%Y-%m-%d") + " Oracle account updates"
        res = @sling.execute_post(@sling.url_for("~admin/message.create.html"), {
          "sakai:type" => "internal",
          "sakai:sendstate" => "pending",
          "sakai:messagebox" => "outbox",
          "sakai:to" => "internal:#{CALCENTRAL_TEAM_GROUP}",
          "sakai:from" => "admin",
          "sakai:subject" => subjectline,
          "sakai:body" => messagebody,
          "sakai:category" => "message"
        })
        if (res.code != "200")
          @log.warn("Not able to send status update: #{res.code}, #{res.body}")
        end
      end
    end

  end

  class Student < ActiveRecord::Base
    self.table_name = "BSPACE_STUDENT_INFO_VW"
    self.primary_key = "student_ldap_uid"
  end
end

if ($PROGRAM_NAME.include? 'oracle_provision_refresh.rb')

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

  end

  optparser.parse ARGV

  odl = MyBerkeleyData::OracleDataLoader.new options

  odl.collect_integrated_accounts
  odl.load_all_students
  odl.load_additional_accounts
  odl.check_stale_accounts
  odl.drop_stale_accounts
  odl.report_activity

end
