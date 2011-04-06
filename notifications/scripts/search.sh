#!/bin/bash

JSON='
{
  "sakai:query-template" : "sakai\\:messagestore:${_userNotificationPath} AND resourceType:myberkeley/notification AND sakai\\:messagebox:${box}",
  "sakai:query-template-options": {
      "sort": "${sortOn} ${sortOrder}"
  },
  "sakai:propertyprovider" : "Notification",
  "sakai:resultprocessor":"Notification",
  "sortOn": "created",
  "sortOrder": "desc",
  "sling:resourceType": "sakai/sparse-search",
  "sakai:title": "Notifications"
}
'

# strip newlines
JSON=`echo "${JSON}" | tr -d '\n'`

curl -u admin:admin "http://localhost:8080/var/notifications/search" -F :operation=import -F :contentType=json \
-F :replace=true -F :replaceProperties=true -F :content="$JSON"

