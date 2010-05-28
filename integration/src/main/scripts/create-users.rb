#!/usr/bin/ruby

$LOAD_PATH << ENV['NAKAMURA_SRC'] + "/testscripts/SlingRuby"

require 'sling/sling'
require 'sling/users'
include SlingInterface
include SlingUsers

users = <<HERE
testuser1
testuser2
HERE

@s = Sling.new()
@um = UserManager.new(@s)
users.each do
  |user| puts "read user #{user}"
  puts @um.create_user(user.chomp)
end



