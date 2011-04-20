#!/bin/bash

LIMIT=1

for ((a=0; a <= LIMIT ; a++))
do
  JSON='
  {
      "sakai:messagebox":"queue",
      "senderID" : "904715",
      "category":"reminder",
      "sendDate":"2011-02-02T12:16:59-01:00",
      "sendState":"pending",
      "dynamicListID":"/a/path/to/a/dynamic/list",
      "uxState" : {
          "validated" : true,
          "eventHour" : 1,
          "eventMin" : 2,
          "eventAMPM" : 3
      },
      "calendarWrapper":{
          "uri":"",
          "etag":"2011-03-16T12:16:59-07:00",
          "component":"VEVENT",
          "isRequired":true,
          "isArchived":false,
          "isCompleted":false,
          "icalData":{
              "DTSTAMP":"2011-03-31T15:15:06-07:00",
              "DTSTART":"2011-05-01T15:15:06-07:00",
              "SUMMARY":"Test event Z",
              "CATEGORIES":["MyBerkeley-Required"],
              "DESCRIPTION":"This is our test description Z"
          }
      }
  }
  '

  # strip newlines
  JSON=`echo "${JSON}" | tr -d '\n' | tr Z ${a}`

  echo curl -e "/dev/test" -u admin:admin "http://localhost:8080/~admin.myb-notificationstore.html" -F notification="$JSON"
  echo
done

