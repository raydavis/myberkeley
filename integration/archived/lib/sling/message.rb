#!/usr/bin/env ruby

require 'digest/sha1'

module SlingMessage

  class MessageManager

    def initialize(sling)
      @sling = sling
    end

    def create(name, type, box = "drafts", props = {})
      @home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{@home}/message.create.html"), props.update("sakai:type" => type, "sakai:to" => name, "sakai:sendstate" => "pending", "sakai:messagebox" => box))
    end
 
    def send(messageId)
      path = "" + messageId[0, 2] + "/" + messageId[2, 2] + "/" + messageId[4,2]+ "/" + messageId[6,2] + "/" + messageId
      return @sling.execute_post(@sling.url_for("#{@home}/message/#{path}.html"), "sakai:messagebox" => "outbox" )
    end

    def list_all_noopts()
      return @sling.execute_get(@sling.url_for("var/message/all.json"))
    end

    def list_all(sortOn = "jcr:created", sortOrder = "descending" )
      return @sling.execute_get(@sling.url_for("var/message/all.json?sortOn="+sortOn+"&sortOrder="+sortOrder))
    end

    def list_inbox(sortOn = "jcr:created", sortOrder = "descending" )
      return @sling.execute_get(@sling.url_for("var/message/box.json?box=inbox&sortOn="+sortOn+"&sortOrder="+sortOrder))
    end

    def list_outbox(sortOn = "jcr:created", sortOrder = "descending" )
      return @sling.execute_get(@sling.url_for("var/message/box.json?box=outbox&sortOn="+sortOn+"&sortOrder="+sortOrder))
    end
	
    
  end

end
