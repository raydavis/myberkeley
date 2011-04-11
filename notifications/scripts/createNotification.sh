#!/bin/bash

JSON='
{
    "sakai:messagebox":"queue",
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
            "DTSTART":"2011-04-15T15:15:06-07:00",
            "SUMMARY":"another tax day party",
            "CATEGORIES":["MyBerkeley-Required"],
            "DESCRIPTION":"this is our taxed description"
        }
    }
}
'

# strip newlines
JSON=`echo "${JSON}" | tr -d '\n'`

curl -e "/dev/test" -u admin:admin "http://localhost:8080/~admin.myb-notificationstore.html" -F notification="$JSON"

