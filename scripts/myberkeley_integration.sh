#!/bin/bash

# script that launches a myberkeley instance and runs its suite
# of integration tests.  

stopAndExit() {
  mvn -P runner -Dsling.stop verify;
  exit 1;
}

echo "Starting myberkeley integration test run at `date`"

# stop the server and clean it out
mvn -P runner -Dsling.stop verify
mvn -Dsling.clean clean || { echo "Sling clean failed." ; stopAndExit ; }

# reinstall
mvn clean install || { echo "mvn clean install failed." ; stopAndExit ; }

# start up 
mvn -P runner -Dsling.start verify || { echo "Failed to start sling." ; stopAndExit ; }

# wait a minute so sling can get going
sleep 60;

# run myberkeley dataloader
mvn -Dsling.loaddata -Dloaddata.server=http://localhost:8080/ -Dloaddata.password=admin -Dloaddata.numusers=16 integration-test || { echo "Integration-test failed." ; stopAndExit ; }

# stop server
mvn -P runner -Dsling.stop verify

echo "Done with integration test at `date`"



