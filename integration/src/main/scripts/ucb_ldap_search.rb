#!/usr/bin/env ruby


require 'rubygems'
require 'net/ldap'
require 'ucb_ldap'
require 'json/pure'  #need to use json_pure because of bug in json/ext 1.4.3

module MyBerkeleyData

# utility for making defined user json data file for sling user creating in sling_data_loader.rb
# currently sling_data_loader.rb does not call this directly but reads the output file.
# this could be easily changed to a direct call but currently am checking in the data file for other possible uses

  def make_defined_users_json
    hits = []
    staff = UCB::LDAP::Entry.search(:base => "ou=people,dc=berkeley,dc=edu", :filter => make_staff_filter)
    puts "found #{staff.length} staff members"
    hits.concat staff
    
    students = UCB::LDAP::Entry.search(:base => "ou=people,dc=berkeley,dc=edu", :filter => make_students_filter)
    puts "found #{students.length} student members"
    hits.concat students
    
    others = UCB::LDAP::Entry.search(:base => "ou=people,dc=berkeley,dc=edu", :filter => make_others_filter)
    puts "found #{others.length} other members"
    hits.concat others
    #hits.sort {|x, y| x.attributes[:sn][0].slice(1) <=> y.attributes[:sn][0].slice(1) }  # this appears to not work, giving up
  
    faculty = UCB::LDAP::Entry.search(:base => "ou=people,dc=berkeley,dc=edu", :filter => make_faculty_filter)
 
    return make_user_json hits 
  end
  
  def write_json(json_data, file_name = "json_data.js")
    json_file = File.new "json_data.js", "w"
    json_file.write json_data
  end
  
  def make_staff_filter
    
    rachael = make_name_filter "Rachel", "Hollowgrass"
    tonyc = make_name_filter "Tony", "Christopher"
    bernie = make_name_filter "Bernadette", "Geuy"
    eli = make_name_filter "Eli", "Cochran"
    oliver = make_name_filter "Oliver", "Heyer"
    ray = make_name_filter "Ray", "Davis"
    johnk = make_name_filter "John F", "King"
    davids = make_name_filter "David", "Scronce"
    owenm = make_name_filter "Owen", "McGrath"
    darlenek = make_name_filter "Darlene", "Kawase"
    kevinc = make_name_filter "Kevin Kwok-Cheong", "Chan"
    gregg = make_name_filter "Greg","German"
    marah = make_name_filter "Mara","Hancock"
    jonh = make_name_filter "Jon", "Hays"
    romans = make_name_filter "Roman V", "Shumikhin"
    ctweney = make_name_filter "Chris", "Tweney"
    susanh = make_name_filter "Susan Janan", "HAGSTROM"
    allison = make_name_filter "Allison", "Bloodworth"
    return rachael | tonyc | bernie | eli | oliver | ray | johnk | davids | owenm | darlenek | kevinc | gregg | marah | jonh | romans | ctweney | susanh | allison
  end
  
  def make_students_filter
    
    mattheww = make_name_filter "Matthew", "Waid"
    michaele= make_name_filter "Michael", "Ellison"
    jessicav = make_name_filter "Jessica B", "Voytek"
    whitneyl = make_name_filter "Whitney", "Lai"
    nicolen = make_name_filter "Nicole", "Ng"
    geobiob = make_name_filter "Geobio", "Boo"
    avneeshk = make_name_filter "Avneesh", "Kohli"
    
    return mattheww | michaele | jessicav | whitneyl | nicolen | geobiob | avneeshk
  end
  
  def make_others_filter 
    adamh = make_name_filter "Adam", "Hochman"
    joshh = make_name_filter "Josh", "Holtzman"
    return adamh | joshh
  end
  
  def make_faculty_filter 
    martinj = make_name_filter "Martin E.", "Jay"
  end
  
  def make_name_filter first, last
    ff = Net::LDAP::Filter.eq("givenname", first.capitalize) 
    lf = Net::LDAP::Filter.eq("sn", last.upcase)
    return ff & lf
  end
  
  
  def make_user_json(entries)
    all_data_hash = {}
    all_users_hash = {}
    entries.each do |entry|
      user_hash = {}
      user_hash["firstName"] = entry.attributes[:givenname][0].capitalize if entry.attributes[:givenname]
      user_hash["lastName"] = entry.attributes[:sn][0].capitalize if entry.attributes[:sn]
      user_hash["email"] = entry.attributes[:mail][0] if entry.attributes[:mail]
      all_users_hash[entry.attributes[:uid][0]] = user_hash
    end
    #all_users_hash.sort{|x, y| x[1]["lastName"] <=> y[1]["lastName"] }
    #puts all_users_hash.inspect
    all_data_hash["users"] = all_users_hash
    users_json = JSON.pretty_generate all_data_hash
    puts users_json
    return users_json
  end
end

if __FILE__ == $0
  include MyBerkeleyData
  json = MyBerkeleyData.make_defined_users_json
  MyBerkeleyData.write_json json
end


