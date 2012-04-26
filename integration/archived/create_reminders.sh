#!/bin/bash

now=`date "+%Y%m%d%H%M%S"`

LIMIT=10
FROM_USER="271592"
FROM_PASS="testuser"
TO_USER="675750" #in this case Message

#reminders
for ((a=6; a <= LIMIT ; a++))
do
     /usr/bin/curl -u ${FROM_USER}:${FROM_PASS} -F sakai:type=notice -F sakai:sendstate=pending -F sakai:messagebox=outbox  -F sakai:to=notice:${TO_USER} -F sakai:from=${FROM_USER} -F sakai:subject="Reminder One.${a}.${now}" -F sakai:body="Reminder Body.${a}.${now}" -F sakai:category=reminder -F sakai:taskState=created -F sakai:dueDate=2010-10-13T15:22:46-07:00 -F sakai:dueDate@TypeHint=Date https://calcentral-qa.berkeley.edu/user/${FROM_USER}/message.create.html
done


messages
for ((a=6; a <= LIMIT ; a++))
do
    /usr/bin/curl -u ${FROM_USER}:${FROM_PASS} -F sakai:type=internal -F sakai:sendstate=pending -F sakai:messagebox=outbox  -F sakai:to=internal:${TO_USER} -F sakai:from=${FROM_USER} -F sakai:subject="Test Message.${a}.${now}" -F sakai:body="Test Message Body.${a}.${now}" -F sakai:category=message https://calcentral-qa.berkeley.edu/user/${FROM_USER}/message.create.html
done