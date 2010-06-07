#!/usr/bin/ruby

require 'sling/sling'
require 'sling/users'
include SlingInterface
include SlingUsers

$num_users = 20;
$test_user_prefix = 'testuser'

users = <<HERE
testuser3
testuser4
HERE

def readNames(fileName)
  f = File.open(fileName, 'r')
  f.readlines
end

def generateUserProps(firstNames, lastNames)
  i = 4
  allUserProps = []
  while i <= $num_users
    userProps = {}
    userProps[':name'] = $test_user_prefix + i.to_s
    firstName = firstNames[rand(firstNames.length)]
    lastName = lastNames[rand(lastNames.length)]
    userProps['firstName'] = firstName.chomp!
    userProps['lastName'] = lastName.chomp!
    userProps['email'] = firstName.downcase + '.' + lastName.downcase + '@berkeley.edu'
    allUserProps[i] = userProps
    i = i +1
  end
  return allUserProps.compact
end

firstNames = readNames("firstNames.txt")
lastNames = readNames("lastNames.txt")

allUsers = generateUserProps firstNames, lastNames



@s = Sling.new("http://localhost:8080/", true, true)
#@s.do_login
@um = UserManager.new(@s)
#allUsers.each do
#  |userProps| puts "creating user #{userProps[':name']}"
#  targetUser = @um.create_user_with_props userProps
#end

allUsers.each do
  |userProps| puts "creating user #{userProps[':name']}"
  targetUser = @um.create_user(userProps[':name'])
  if (targetUser.nil?) then targetUser = User.new(userProps[':name']) end
  userName = userProps.delete ':name'
  puts "updating user #{userName} properties #{userProps.inspect}"
  targetUser.update_properties @s, userProps
end


        #post("/system/userManager/user.create.html",
        #    ":name", userId,
        #    "firstName", firstName,
        #    "lastName", lastName,
        #    "email", email,
        #    "pwd", "testPwd",
        #    "pwdConfirm", "testPwd"
        #);
