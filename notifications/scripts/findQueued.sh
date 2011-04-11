#!/bin/bash

JSON='
{
  "sakai:query-template" : "sling\\:resourceType:myberkeley/notification AND sakai\\:messagebox:queue",
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

curl -e "/dev/test" -u admin:admin "http://localhost:8080/var/notifications/findqueued" -F :operation=import -F :contentType=json \
-F :replace=true -F :replaceProperties=true -F :content="$JSON"

