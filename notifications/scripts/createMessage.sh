#!/bin/bash

LIMIT=1

for ((a=0; a <= LIMIT ; a++))
do
  JSON='
  {
      "sakai:messagebox":"queue",
      "senderID" : "904715",
      "type":"message",
      "sendDate":"2011-02-02T12:16:59-01:00",
      "sendState":"pending",
      "dynamicListID":"/~904715/private/dynamic_lists/dl-904715-1308350730880",
      "uxState" : {
          "validated" : true,
          "eventHour" : 1,
          "eventMin" : 2,
          "eventAMPM" : 3
      },
      "body":"Message body Z",
      "subject":"Subject Z"
  }
  '

  # strip newlines
  JSON=`echo "${JSON}" | tr -d '\n' | tr Z ${a}`

  curl -e "/dev/test" -u admin:admin "http://localhost:8080/~admin.myb-notificationstore.html" -F notification="$JSON"
  echo
done

