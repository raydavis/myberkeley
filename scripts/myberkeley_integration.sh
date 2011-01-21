#!/bin/bash

# script that launches a myberkeley instance and runs its suite
# of integration tests.  

PORT="-Dsling.port=8899"

stopAndExit() {
  mvn -P runner -Dsling.stop $PORT verify;
  exit 1;
}

echo "Starting myberkeley integration test run at `date`"

# stop the server and clean it out
mvn -P runner -Dsling.stop $PORT verify
mvn -Dsling.clean clean || { echo "Sling clean failed." ; stopAndExit ; }

# reinstall
mvn clean install || { echo "mvn clean install failed." ; stopAndExit ; }

# start up 
mvn -P runner -Dsling.start $PORT verify || { echo "Failed to start sling." ; stopAndExit ; }

# wait a minute so sling can get going
sleep 60;

# run myberkeley dataloader
mvn -Dsling.loaddata $PORT -Dloaddata.server=http://localhost:8899/ -Dloaddata.password=admin -Dloaddata.numusers=16 integration-test || { echo "Integration-test failed." ; stopAndExit ; }

# stop server
mvn -P runner -Dsling.stop $PORT verify

echo "Done with integration test at `date`"



