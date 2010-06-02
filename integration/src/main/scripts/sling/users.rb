#!/usr/bin/env ruby

require 'digest/sha1'

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
      if ( @path == nil )
        props = sling.get_node_props(group_url)
        @path = props["path"]
      end
      return "/_group"+@path
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

    def self.admin_user
      return User.new("admin", "admin")
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
	
	
	def change_password(sling, newpassword)
	   return sling.execute_post(sling.url_for("#{user_url}.changePassword.html"), "oldPwd" => @password, "newPwd" => newpassword, "newPwdConfirm" => newpassword)     
	end
	

    # Get the home folder of a group.
    def home_path_for(sling)
      if ( @path == nil )
        props = sling.get_node_props(user_url)
        @path = props["path"]
      end
      return "/_user"+@path
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

    def initialize(sling)
      @sling = sling
      @date = Time.now().strftime("%Y%m%d%H%M%S")
    end

    def delete_test_user(id)
      return delete_user("testuser#{@date}-#{id}")
    end
     
    def delete_user(username)
      result = @sling.execute_post(@sling.url_for("#{User.url_for(username)}.delete.html"),
                                    { "go" => 1 })
      if (result.code.to_i > 299)
        puts "Error deleting user"
        return false
      end
      return true
    end
 
    def delete_group(groupname)
      result = @sling.execute_post(@sling.url_for("#{Group.url_for(groupname)}.delete.html"),
                                    { "go" => 1 })
      if (result.code.to_i > 299)
        puts "Error deleting group"
        return false
      end
      return true
    end
   
    def create_test_user(id)
      return create_user("testuser#{@date}-#{id}")
    end

    def create_user(username)
      puts "Creating user: #{username}"
      user = User.new(username)
      result = @sling.execute_post(@sling.url_for("#{$USER_URI}"),
                            { ":name" => user.name,
                              "pwd" => user.password,
                              "pwdConfirm" => user.password })
      if (result.code.to_i > 299)
        puts "Error creating user"
        return nil
      end
      return user
    end

    def create_group(groupname)
      puts "Creating group: #{groupname}"
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
