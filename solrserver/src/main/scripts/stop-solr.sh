#!/bin/bash

JPS_RESULTS=`jps | grep start.jar | cut -d ' ' -f 1`
for i in $JPS_RESULTS
do
  echo "Stopping server $i"
  kill $i
done
