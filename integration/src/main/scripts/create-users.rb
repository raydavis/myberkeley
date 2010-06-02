#!/usr/bin/ruby

require 'sling/sling'
require 'sling/users'
include SlingInterface
include SlingUsers

users = <<HERE
testuser3
testuser4
HERE

@s = Sling.new()
@um = UserManager.new(@s)
users.each do
  |user| puts "read user #{user}"
  puts @um.create_user(user.chomp)
end



