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
  
  class SlingDataLoader
    CONTEXTS_TO_UPGRADE = ["g-ced-advisors","g-ced-students"]
    TEMPLATE_FINAL_TERMS = ["_navigation","_pages","_widgets"]  # the template nodes to be copied in the user's pages node to create personal pages
    
    @ced_advisors_group = nil                                                                               
    @ced_all_students_group = nil
    
    attr_reader :user_password_key
  
    def initialize(server, admin_password="admin")
      @server = server
      @sling = Sling.new(server, admin_password, true)
      @sling.do_login
      @user_manager = UserManager.new(@sling)
      @authz = SlingAuthz::Authz.new(@sling)
    end

#    curl -u admin:admin -F:operation=copy -F:dest=/_user/2/27/271592/pages/ http://localhost:8080/var/templates/site/defaultuser/_pages
#    curl -u admin:admin -F:operation=copy -F:dest=/_user/2/27/271592/pages/ http://localhost:8080/var/templates/site/defaultuser/_widgets
#    curl -u admin:admin -F:operation=copy -F:dest=/_user/2/27/271592/pages/ http://localhost:8080/var/templates/site/defaultuser/_widgets
#    we need to copy the defaultuser site templates to the provisioned users lacking them - see curl statements above
#    this is because existing create_users scripts don't include the ":sakai:pages-template" directive for some reason
#     so we need to upgrade the existing users so their personal pages will display correctly
#    we're going to have do do a vanilla post copy because the PagesAuthorizablePostProcessor only copies template files on a Create operation
#  and these users already exist
    def upgrade_personal_pages
      CONTEXTS_TO_UPGRADE.each do |context|
        print "attempting to upgrade users in context #{context}\n"
        upgrade_user_paths = find_users_paths context  #/_user/2/27/271592 system/userManager/   "jcr:path": "/~300846/public/authprofile"
        print "about to upgrade #{upgrade_user_paths.length} users in context #{context} personal pages using templates #{TEMPLATE_FINAL_TERMS.inspect}\n"
        upgrade_user_paths.each do |user_path|
          TEMPLATE_FINAL_TERMS.each do |term|  
            copy_response = @sling.execute_post("#{@server}/var/templates/site/defaultuser/#{term}", ":operation" => "copy", ":dest" => "/_user/#{user_path}/pages/")
            if (copy_response.code.to_i >= 300)
              print "upgrade #{term} pages copy failed for user_path #{user_path}\n"
            end
          end
        end
      end
    end
    
#    find the full path to user home as we can't use the PagesAuthorizablePostProcessor on already existing pages nodes
#   so we need to do a vanilla post copy and need full path for that
    def find_users_paths(context)
      user_paths = []
      page = 1
      until (page >= 40)
        if (context == 'g-ced-students')
          search_string = "#{@server}/var/search/users.json?q=#{context}&items=25&page=#{page}"
        else
          search_string = "#{@server}/var/search/users.json?q=#{context}"
        end
        response = @sling.execute_get(search_string)
        response_json = JSON response.body
        user_nodes = response_json["results"]
        print "found #{user_nodes.length} users to upgrade with search #{search_string}\n"
        page += 1
        user_nodes.each do |user_node|
          user_id = user_node["rep:userId"]
          # user_id = jcr_path.split(/^\/~|\/.*$/)[1].to_s
          user_path_response = @sling.execute_get("#{@server}/system/userManager/user/#{user_id}.json")  # the URL where full paths reside
          if (user_path_response.code.to_i >= 200 && user_path_response.code.to_i < 300) 
            user_path_json = JSON user_path_response.body
            print user_path_json
            user_path = user_path_json["path"]
            user_paths.push user_path
          else
            print "couldn't get user_path for user #{user_id}, will not upgrade\n"
          end
        end
      end
      return user_paths
    end
  end
end
    


if ($PROGRAM_NAME.include? 'sling_data_upgrader.rb')
  puts "will upgrade personal pages data on server #{ARGV[0]}"
  sdl = MyBerkeleyData::SlingDataLoader.new ARGV[0], ARGV[1]
  sdl.upgrade_personal_pages
end