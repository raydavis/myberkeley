#!/usr/bin/env ruby
require 'net/http'
require 'cgi'
require 'rubygems'
require 'json'
require 'curb'
require 'yaml'
require 'sling/users'
require 'sling/sites'
require 'logger'

class String
  def base64_decode
    unpack('m').first
  end
  
  def base64_encode
    [self].pack('m').chop
  end
end


class Hash
  
  def dump
    return keys.collect{|k| "#{k} => #{self[k]}"}.join(", ")
  end
  
end

## Fix array handling
module Net::HTTPHeader
  def set_form_data(params, sep = '&')
    self.body = params.map {|k, v| encode_kvpair(k, v) }.flatten.join(sep)
    self.content_type = 'application/x-www-form-urlencoded'
  end
  
  def encode_kvpair(k, vs)
    Array(vs).map {|v| "#{urlencode(k)}=#{urlencode(v.to_s)}" }
  end
end

class WrappedCurlResponse
  
  def initialize(response)
    @response = response
  end
  
  def code
    return @response.response_code
  end
  
  def message
    return @response.response_code
  end
  
  def body
    return @response.body_str
  end
  
end

module SlingInterface
  
  class Sling
    
    attr_accessor :log, :trustedauth
    
    def initialize(server="http://localhost:8080/", trustedauth=false)
      @server = server
      @user = SlingUsers::User.admin_user()
      @trustedauth = trustedauth
      @trustedcookie == nil
      @log = Logger.new(STDOUT)
      @log.level = Logger::INFO
    end
    
    def dump_response(response)
      @log.info "Response: #{response.code} #{response.message}"
      @log.debug "#{response.body}"
    end
    
    def switch_user(user)
      @log.info "Switched user to #{user}"
      @user = user
      if ( @trustedauth ) 
         @trustedcookie = nil
      end
    end
    
    
    def text_to_multipart(key,value)
      return "Content-Disposition: form-data; name=\"#{CGI::escape(key)}\"\r\n" + 
             "\r\n" + 
             "#{value}\r\n"
    end
    
    def file_to_multipart(key,filename,mime_type,content)
      if ( filename != nil ) 
        return "Content-Disposition: form-data; name=\"*\"; filename=\"#{filename}\"\r\n" +
             "Content-Transfer-Encoding: binary\r\n" +
             "Content-Type: #{mime_type}\r\n" + 
             "\r\n" + 
             "#{content}\r\n"
      else 
        return "Content-Disposition: form-data; name=\"jcr:content\"\r\n" +
             "Content-Transfer-Encoding: binary\r\n" +
             "Content-Type: #{mime_type}\r\n" + 
             "\r\n" + 
             "#{content}\r\n"
      end

    end
    
    def execute_file_post(path, fieldname, filename, data, content_type)
      uri = URI.parse(path)
      fileTypeHint = "Content-Disposition: form-data; name=\"*@TypeHint\"\r\n\r\n" +
                     "nt:file\r\n"

      params = [fileTypeHint,file_to_multipart(fieldname, filename, content_type, data)]
      boundary = '349832898984244898448024464570528145'
      query = params.collect {|p| '--' + boundary + "\r\n" + p}.join('') + "--" + boundary + "--\r\n"
      req = Net::HTTP::Post.new(uri.path)
      if ( @trustedauth ) 
        if ( @trustedcookie == nil ) 
            do_login()
        end
        res = Net::HTTP.new(uri.host, uri.port).start {|http| http.request_post(path,query,"Content-type" => "multipart/form-data; boundary=" + boundary, "Cookie" => @trustedcookie) }
      else 
        @user.do_request_auth(req)
        pwd = "#{@user.name}:#{@user.password}"
        pwd = pwd.base64_encode()
        res = Net::HTTP.new(uri.host, uri.port).start {|http| http.request_post(path,query,"Content-type" => "multipart/form-data; boundary=" + boundary, "Authorization" => "Basic #{pwd}") }
      end
      dump_response(res)
      return res
    end
    
    def delete_file(path) 
      uri = URI.parse(path)
      req = Net::HTTP::Delete.new(uri.path)
      if ( @trustedauth ) 
        if ( @trustedcookie == nil ) 
            do_login()
        end
		req["Cookie"] = @trustedcookie
        res = Net::HTTP.new(uri.host, uri.port).start { |http| http.request(req ) }
      else
        @user.do_request_auth(req)
        res = Net::HTTP.new(uri.host, uri.port).start { |http| http.request(req) }
      end
      dump_response(res)
      return res
    end
    
    def execute_put_file(path, data)
      @log.debug "URL: #{path}"
      @log.debug("PUTFILE: #{path} (as '#{@user.name}')")
      uri = URI.parse(path)
      req = Net::HTTP::Put.new(uri.path)
      if ( @trustedauth ) 
        if ( @trustedcookie == nil ) 
            do_login()
        end
		req["Cookie"] = @trustedcookie
        res = Net::HTTP.new(uri.host, uri.port).start{ |http| http.request(req, data) }
      else
        @user.do_request_auth(req)
        res = Net::HTTP.new(uri.host, uri.port).start{ |http| http.request(req, data) }
      end
      dump_response(res)
      return res
    end
    
    def execute_post(path, post_params={})
      # We always post with utf-8
      if ( post_params["_charset_"] == nil)
        post_params["_charset_"] = "utf-8"
      end
      @log.debug("POST: #{path} (as '#{@user.name}')\n\tparams: #{post_params.dump}")
      uri = URI.parse(path)
      req = Net::HTTP::Post.new(uri.path)
      if ( @trustedauth ) 
        if ( @trustedcookie == nil ) 
            do_login()
        end
		req["Cookie"] = @trustedcookie
        req.set_form_data(post_params)
        res = Net::HTTP.new(uri.host, uri.port).start{ |http| http.request(req) }
      else
        @user.do_request_auth(req)
        req.set_form_data(post_params)
        res = Net::HTTP.new(uri.host, uri.port).start{ |http| http.request(req) }
      end
      dump_response(res)
      return res
    end
    
    def execute_get(path, query_params=nil)
      if (query_params != nil)
        param_string = query_params.collect { |k,v|
          val = case v
            when String then
            v
            when Numeric then
            v.to_s
          else
            v.to_json
          end
          CGI.escape(k) + "=" + CGI.escape(val)
        }.join("&")
        path = "#{path}?#{param_string}" 
      end
      @log.debug "URL: #{path}"
      uri = URI.parse(path)
      path = uri.path
      path = path + "?" + uri.query if uri.query
      @log.debug("GET: #{path} (as '#{@user.name}')")
      req = Net::HTTP::Get.new(path)
      if ( @trustedauth ) 
        if ( @trustedcookie == nil ) 
            do_login()
        end
		req["Cookie"] = @trustedcookie
        res = Net::HTTP.new(uri.host, uri.port).start { |http| http.request(req) }
      else
        @user.do_request_auth(req)
        res = Net::HTTP.new(uri.host, uri.port).start { |http| http.request(req) }
      end
      dump_response(res)
      return res
    end
  
    def do_login() 
	  path = url_for("/system/sling/formlogin")
      req = Net::HTTP::Post.new(path)
      uri = URI.parse(path)
      req.set_form_data({ "sakaiauth:un" => @user.name, "sakaiauth:pw" => @user.password, "sakaiauth:login" => 1 })
      res = Net::HTTP.new(uri.host, uri.port).start{ |http| http.request(req) }
      if ( res.code == "200" ) 
        @trustedcookie = res["Set-Cookie"]
	    @log.info("Login Ok, cookie was  ["+@trustedcookie+"]")
      else
	    @log.info("Failed to perform login, got "+res.code+" response code")
	  end
    end
    
    def execute_get_with_follow(url)
      found = false
      uri = URI.parse(url)
      until found
        host, port = uri.host, uri.port if uri.host && uri.port
        req = Net::HTTP::Get.new(uri.path)
        if ( @trustedauth ) 
          if ( @trustedcookie == nil ) 
            do_login()
          end
		  req["Cookie"] = @trustedcookie
          res = Net::HTTP.start(host, port) {|http|  http.request(req) }
        else 
          @user.do_request_auth(req)
          res = Net::HTTP.start(host, port) {|http|  http.request(req) }
        end
        if res.header['location']
          @log.info "Got Redirect: #{res.header['location']}"
          uri = URI.parse(res.header['location']) 
        else
          found = true
        end
      end 
      dump_response(res)
      return res
    end
    
    def url_for(path)
      if (path.slice(0,1) == '/')
        path = path[1..-1]
      end
      return "#{@server}#{path}"
    end
    
    def update_properties(principal, props)
      principal.update_properties(self, props)
    end
    
    def delete_node(path)
      result = execute_post(url_for(path), ":operation" => "delete")
    end
    
    def create_file_node(path, fieldname, filename, data, content_type="text/plain")
      result = execute_file_post(url_for(path), fieldname, filename, data, content_type)
    end
    
    def create_node(path, params)
      result = execute_post(url_for(path), params)
    end
    
    def get_user()
      return @user
    end
    
    def get_node_props_json(path)
      @log.debug "Getting props for path: #{path}"
      result = execute_get(url_for("#{path}.json"))
      if ( result.code == "200" ) 
        return result.body
      end 
      @log.info("Failed to get properties for "+path+" cause "+result.code+"\n"+result.body)
      return "{}"
    end
    
    def get_node_props(path)
      return JSON.parse(get_node_props_json(path))
    end
    
    def update_node_props(path, props)
      return execute_post(url_for(path), props)
    end
    
    def get_node_acl_json(path)
      return execute_get(url_for("#{path}.acl.json")).body
    end

    def get_node_ruleacl_json(path)
      return execute_get(url_for("#{path}.ruleacl.json")).body
    end
    
    def get_node_acl(path)
      return JSON.parse(get_node_acl_json(path))
    end
 
    def get_node_ruleacl(path)
      return JSON.parse(get_node_ruleacl_json(path))
    end
    
    def set_node_acl_entries(path, principal, privs)
      @log.info "Setting node acl for: #{principal} to #{privs.dump}"
      res = execute_post(url_for("#{path}.modifyAce.html"), 
      { "principalId" => principal.name }.update(
                                                 privs.keys.inject(Hash.new) do |n,k| 
        n.update("privilege@#{k}" => privs[k])
        end))
        return res
      end

    def set_node_acl_rule_entries(path, principal, privs, props)
      @log.info "Setting node acl for: #{principal} to #{privs.dump}"
      props["principalId"] = principal.name
      res = execute_post(url_for("#{path}.modifyRuleAce.html"), 
        props.update(
          privs.keys.inject(Hash.new) do |n,k| 
            n.update("privilege@#{k}" => privs[k])
          end))
      return res
    end
      
      def delete_node_acl_entries(path, principal)
        res = execute_post(url_for("#{path}.deleteAce.html"), {
              ":applyTo" => principal
        })
      end
      
      def clear_acl(path)
        acl = JSON.parse(get_node_acl_json(path))
        acl.keys.each { |p| delete_node_acl_entries(path, p) }
      end
      
      def save_node(path)
        res = execute_post(url_for("#{path}.save.json"), {})
        if (res.code == "200")
          return JSON.parse(res.body)["versionName"]
        end
        return nil
      end
      
      def versions(path)
        return get_node_props("#{path}.versions")["versions"].keys
      end
      
      def version(path, version, extension)
        return execute_get(url_for("#{path}.version.,#{version},.#{extension}"))
      end
      
    end
    
  end
  
  if __FILE__ == $0
    @log.info "Sling test"
    s = SlingInterface::Sling.new("http://localhost:8080/", false)
    um = SlingUsers::UserManager.new(s)
    um.create_group(10)
    user = um.create_test_user(10)
    s.create_node("fish", { "foo" => "bar", "baz" => "jim" })
    @log.info s.get_node_props_json("fish")
    @log.info s.get_node_acl_json("fish")
    
    s.set_node_acl_entries("fish", user, { "jcr:write" => "granted" })
    @log.info s.get_node_acl_json("fish")
  end
