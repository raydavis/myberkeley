#!/usr/bin/env ruby

require 'logger'

module SlingAuthz

  class Authz

    attr_accessor :log

    def initialize(sling)
      @sling = sling
      @log = Logger.new(STDOUT)
      @log.level = Logger::WARN
    end

    def delete(path,principal)
	postParams = {}
	  postParams[':applyTo'] = [principal]
	  urlpath = @sling.url_for(path)	  
	  res = @sling.execute_post(urlpath+".deleteAce.html", postParams)
	  if ( res.code != "200" )
	     @log.debug(res.body)
	     @log.info(" Unable to update acl at #{path} "+postParams)
		 return false 
	  end 

	end

    def grant(path,principal,privilege)
	  
	  acl = getacl(path)
	  ace = {}
	  if ( acl[principal] )
	     @log.info(acl[principal])
		 ace = acl[principal]
	  end
	  postParams = {}
	  postParams['principalId'] = principal
	  
	  # Save the current ACE
	  ace.each do | key, value |
	    if ( key == "granted" || key == "denied" )
		  value.each do | priv |
		    postParams['privilege@'+priv] = key
		  end 
		end
	  end
	  
	  # Add in the new ACE
	  privilege.each do | key, value |
		postParams['privilege@'+key] = value
	  end
	  
	  @log.info("Updating ACE to :"+hashToString(postParams))

		
	  urlpath = @sling.url_for(path)	  
	  res = @sling.execute_post(urlpath+".modifyAce.html", postParams)
	  if ( res.code != "200" )
	     @log.debug(res.body)
	     @log.info(" Unable to update acl at #{path} "+postParams)
		 return false 
	  end 
    end
	
	def getacl(path)
	  urlpath = @sling.url_for(path)
	  res = @sling.execute_get(urlpath+".acl.json")
	  if ( res.code != "200" )
	     log.info(" Unable to get ACL at path #{path} ")
		 return false
	  end
	  @log.info("Current ACE :"+res.body)
	  
	  acl = JSON.parse(res.body)
	  return acl
	end
	
	def hashToString(hashVar) 
	  fs = "{"
	  hashVar.each do | key, value |
	    fs = fs + '"' + key + '" => "'+value.to_s+'",' 
	  end
	  fs = fs + "}"
	end
	


  end


end
