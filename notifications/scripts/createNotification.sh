#!/bin/bash

JSON='
{
    "uri":"/some/sample/uri",
    "isCompleted":true,
    "isArchived":true
}
'

# strip newlines
JSON=`echo "${JSON}" | tr -d '\n'`

curl -u admin:admin "http://localhost:8080/~904715.myb-notificationstore.html" -F notification="$JSON"

