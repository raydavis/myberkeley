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
  |user| puts "deleting user #{user}"
  puts @um.delete_user(user.chomp)
end



