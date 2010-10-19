#!/usr/bin/env ruby

require 'digest/sha1'
require 'logger'

$USERMANAGER_URI="system/userManager/"
$GROUP_URI="#{$USERMANAGER_URI}group.create.html"
$USER_URI="#{$USERMANAGER_URI}user.create.html"
$DEFAULT_PASSWORD="testuser"

module SlingUsers

  class Principal

    attr_accessor :name

    def initialize(name)
      @name = name
    end
    
    
    # Get the public path for a user
    def public_path_for(sling)
      return home_path_for(sling) + "/public"
    end
    
    # Get the private path for a user
    def private_path_for(sling)
      return home_path_for(sling) + "/private"
    end
    
    def message_path_for(sling,messageid)
      return home_path_for(sling) + "/message/"+messageid[0,2]+"/"+messageid[2,2]+"/"+messageid[4,2]+"/"+messageid[6,2]+"/"+messageid
    end

  end
  

  class Group < Principal
    def to_s
      return "Group: #{@name}"
    end

    def update_properties(sling, props)
      return sling.execute_post(sling.url_for("#{group_url}.update.html"), props)
    end

    def add_member(sling, principal, type)
      principal_path = "/#{$USERMANAGER_URI}#{type}/#{principal}"
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":member" => principal_path })
    end

    def add_members(sling, principals)
      principal_paths = principals.collect do |principal|
        if principal.index("g-") == 0
          type = "group"
        else
          type = "user"
        end
        "/#{$USERMANAGER_URI}#{type}/#{principal}"
      end
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":member" => principal_paths })
    end

    def add_manager(sling, principal)
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":manager" => principal })
    end

    def add_viewer(sling, principal)
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":viewer" => principal })
    end

    def details(sling)
      return sling.get_node_props(group_url)
    end

    def remove_member(sling, principal, type)
      principal_path = "/#{$USERMANAGER_URI}#{type}/#{principal}"
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":member@Delete" => principal_path })
    end

    def has_member(sling, principal)
      detail = self.details(sling)
      members = detail["members"]
      if (members == nil)
        return false
      end
      return members.include?(principal)
    end

    def remove_manager(sling, principal)
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":manager@Delete" => principal })
    end

    def remove_viewer(sling, principal)
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":viewer@Delete" => principal })
    end

    def remove_members(sling, principals)
      principal_paths = principals.collect do |principal|
        if principal.index("g-") == 0
          type = "group"
        else
          type = "user"
        end
        "/#{$USERMANAGER_URI}#{type}/#{principal}"
      end
      return sling.execute_post(sling.url_for("#{group_url}.update.html"),
              { ":member@Delete" => principal_paths })
    end

    def set_joinable(sling, joinable)
      return sling.execute_post(sling.url_for("#{group_url}.update.html"), "sakai:joinable" => joinable)
    end

    def members(sling)
      props = sling.get_node_props(group_url)
      return props["members"]
    end

    # Get the home folder of a group.
    def home_path_for(sling)
      return "/~#{@name}"
    end

    def self.url_for(name)
      return "#{$USERMANAGER_URI}group/#{name}"
    end

    private
    def group_url
      return Group.url_for(@name)
    end
  end

  class Owner < Principal
	def initialize()
		super("owner")
	end
  end
  
  class User < Principal
    attr_accessor :password

    def initialize(username, password=$DEFAULT_PASSWORD)
      super(username)
      @password = password
    end

    def self.admin_user(admin_password = "admin")
      return User.new("admin", admin_password)
    end

    def self.anonymous
      return AnonymousUser.new
    end

    def do_request_auth(req)
      req.basic_auth(@name, @password)
    end
  
    def do_curl_auth(c)
      c.userpwd = "#{@name}:#{@password}"
    end

    def to_s
      return "User: #{@name} (pass: #{@password})"
    end

    def update_properties(sling, props)
      return sling.execute_post(sling.url_for("#{user_url}.update.html"), props)
    end
    
    def update_profile_properties(sling, user_props)
      firstname = user_props['firstName']
      lastname = user_props['lastName']
      email = user_props['email']
      data = {}
      data[":sakai:profile-import"] = "{ 'basic': { 'access': 'everybody', 'elements': { 'email': { 'value': '#{email}' }, 'firstName': { 'value': '#{firstname}' }, 'lastName': { 'value': '#{lastname}' } } }, 'myberkeley': { 'access': 'principal', 'elements': { 'context': { 'value': '#{user_props['context']}' }, 'standing': { 'value': '#{user_props['standing']}' }, 'current': { 'value': '#{user_props['current']}' }, 'major': { 'value': '#{user_props['major']}' }, 'participant': { 'value': '#{user_props['participant']}' } } } }"
      return sling.execute_post(sling.url_for("#{user_url}.update.html"), data)
    end
	
	
    def change_password(sling, newpassword)
       return sling.execute_post(sling.url_for("#{user_url}.changePassword.html"), "oldPwd" => @password, "newPwd" => newpassword, "newPwdConfirm" => newpassword)     
    end
	

    # Get the home folder of a group.
    def home_path_for(sling)
      return "/~#{@name}"
    end

    
    def self.url_for(name)
      return "#{$USERMANAGER_URI}user/#{name}"
    end

    private
    def user_url
      return User.url_for(@name)
    end
  end

  class AnonymousUser < User

    def initialize()
      super("anonymous", "none")
    end

    def do_curl_auth(c)
      # do nothing
    end
    
    def do_request_auth(r)
      # do nothing
    end
  
  end

  class UserManager

    attr_accessor :log

    def initialize(sling)
      @sling = sling
      @date = Time.now().strftime("%Y%m%d%H%M%S")
      @log = Logger.new(STDOUT)
      @log.level = Logger::INFO
    end

    def delete_test_user(id)
      return delete_user("testuser#{@date}-#{id}")
    end
     
    def delete_user(username)
      result = @sling.execute_post(@sling.url_for("#{User.url_for(username)}.delete.html"),
                                    { "go" => 1 })
      if (result.code.to_i > 299)
        @log.info "Error deleting user"
        return false
      end
      return true
    end
 
    def delete_group(groupname)
      result = @sling.execute_post(@sling.url_for("#{Group.url_for(groupname)}.delete.html"),
                                    { "go" => 1 })
      if (result.code.to_i > 299)
        @log.info "Error deleting group"
        return false
      end
      return true
    end
   
    def create_test_user(id)
      return create_user("testuser#{@date}-#{id}")
    end

    def create_user(username, firstname = nil, lastname = nil)
      @log.info "Creating user: #{username}"
      user = User.new(username)
      data = { ":name" => user.name,
              "pwd" => user.password,
              "pwdConfirm" => user.password }
      if (!firstname.nil? and !lastname.nil?)
        data[":sakai:profile-import"] = "{'basic': {'access': 'everybody', 'elements': {'email': {'value': '#{username}@sakai.invalid'}, 'firstName': {'value': '#{firstname}'}, 'lastName': {'value': '#{lastname}'}}}}"
      end
      result = @sling.execute_post(@sling.url_for("#{$USER_URI}"), data)
      if (result.code.to_i > 299)
        @log.info "Error creating user"
        return nil
      end
      return user
    end

   def create_user_with_props(username, user_props, password=nil)
      puts "Creating user: #{username}"
      if (password)
        user = User.new username, password
      else
        user = User.new username
      end
      user_props[":name"] = username
      user_props["pwd"] = user.password
      user_props["pwdConfirm"] = user.password
      firstname = user_props['firstName']
      lastname = user_props['lastName']
      email = user_props['email']
      data = {}
      if (!firstname.nil? and !lastname.nil?)
        data[":sakai:profile-import"] = "{ 'basic': { 'access': 'everybody', 'elements': { 'email': { 'value': '#{email}' }, 'firstName': { 'value': '#{firstname}' }, 'lastName': { 'value': '#{lastname}' } } }, 'myberkeley': { 'access': 'principal', 'elements': { 'context': { 'value': '#{user_props['context']}' }, 'standing': { 'value': '#{user_props['standing']}' }, 'current': { 'value': '#{user_props['current']}' }, 'major': { 'value': '#{user_props['major']}' }, 'participant': { 'value': '#{user_props['participant']}' } } } }"
      end
      result = @sling.execute_post(@sling.url_for("#{$USER_URI}"), data)
      if (result.code.to_i > 299)
        puts "Error creating user #{username}"
        return nil
      end
      return user
    end
    
    def create_group(groupname)
      @log.info "Creating group: #{groupname}"
      group = Group.new(groupname)
      result = @sling.execute_post(@sling.url_for($GROUP_URI), { ":name" => group.name })
      if (result.code.to_i > 299)
        return nil
      end
      return group
    end

    def get_user_props(name)
      return @sling.get_node_props(User.url_for(name))
    end

    def get_group_props(name)
      return @sling.get_node_props(Group.url_for(name))
    end

  end

end
