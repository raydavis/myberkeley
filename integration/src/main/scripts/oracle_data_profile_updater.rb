#!/usr/bin/env ruby

# TODO This is currently broken, since it refers to the old "sling_data_loader"
# (now called "ucb_data_loader").

require 'oracle_data_loader'

module MyBerkeleyData
  
  class OracleDataProfileUpdater < OracleDataLoader
    
    def initialize options
      super(options)
    end
    
    def update_ced_students
      ced_students =  select_ced_students
      ced_students.each do |s|
        props = make_user_props s
        if (props['current'] == true)
          student_ldap_uid = s.student_ldap_uid
          user = @sling_data_loader.update_user student_ldap_uid, props
        end
      end
    end
    
    def update_ced_advisors
        response = @sling.execute_get(@sling.url_for("/var/search/users.json?q='g-ced-advisors'"))
        response_json = JSON response.body
        advisor_nodes = response_json["results"]
        advisor_nodes.each do |advisor_node|
          advisor_id = advisor_node["rep:userId"]
          user_props = {}
          basic_elements = advisor_node['basic']['elements']
          last_name = basic_elements['lastName']['value']
          first_name = basic_elements['firstName']['value']
          email = basic_elements['email']['value']
          user_props['firstName'] = first_name
          user_props['lastName'] = last_name
          user_props['email'] = email
          @sling_data_loader.make_advisor_props user_props
          updated_advisor = @sling_data_loader.update_user advisor_id, user_props
        end
    end
  end
end
  
if ($PROGRAM_NAME.include? 'oracle_data_profile_updater.rb')
  
  options = {}
  optparser = OptionParser.new do |opts|
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
  
  optparser.parse ARGV
  
  odl = MyBerkeleyData::OracleDataProfileUpdater.new options
  
  #odl.update_ced_students
  odl.update_ced_advisors
end 