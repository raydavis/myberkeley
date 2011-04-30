#!/usr/bin/env ruby

module SlingContacts

  class ContactManager

    def initialize(sling)
      @sling = sling
    end

    def invite_contact(name, sharedRelationships, fromRelationships=[], toRelationships=[])
      case sharedRelationships
        when String
        sharedRelationships = [sharedRelationships]
      end
      home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{home}/contacts.invite.html"), "sakai:types" => sharedRelationships,
        "fromRelationships" => fromRelationships, "toRelationships" => toRelationships, "targetUserId" => name)
    end
 
    def accept_contact(name)
      home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{home}/contacts.accept.html"), {"targetUserId" => name})
    end

    def reject_contact(name)
      home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{home}/contacts.reject.html"), {"targetUserId" => name})
    end

    def ignore_contact(name)
      home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{home}/contacts.ignore.html"), {"targetUserId" => name})
    end

    def block_contact(name)
      home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{home}/contacts.block.html"), {"targetUserId" => name})
    end

    def remove_contact(name)
      home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{home}/contacts.remove.html"), {"targetUserId" => name})
    end

    def cancel_invitation(name)
      home = @sling.get_user().home_path_for(@sling)
      return @sling.execute_post(@sling.url_for("#{home}/contacts.cancel.html"), {"targetUserId" => name})
    end


    def get_accepted()
      return @sling.get_node_props("var/contacts/accepted")
    end

    def get_pending()
      return @sling.get_node_props("var/contacts/pending")
    end

    def get_invited()
      return @sling.get_node_props("var/contacts/invited")
    end

    def get_blocked()
      return @sling.get_node_props("var/contacts/blocked")
    end

    def get_ignored()
      return @sling.get_node_props("var/contacts/ignored")
    end


    def get_all()
      return @sling.get_node_props("var/contacts/all")
    end
    
  end

end
