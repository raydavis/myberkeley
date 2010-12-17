#!/bin/bash

stopAndExit() {
  mvn -P runner -Dsling.stop verify;
  exit 1;
}

echo "Starting myberkeley integration test run at `date`"

mvn -P runner -Dsling.stop verify

mvn -Dsling.clean clean || { echo "Sling clean failed." ; stopAndExit ; }

mvn clean install || { echo "mvn clean install failed." ; stopAndExit ; }

mvn -P runner -Dsling.start verify || { echo "Failed to start sling." ; stopAndExit ; }

sleep 60;

mvn -Dsling.loaddata -Dloaddata.server=http://localhost:8080/ -Dloaddata.password=admin -Dloaddata.numusers=16 integration-test || { echo "Integration-test failed." ; stopAndExit ; }

mvn -P runner -Dsling.stop verify

echo "Done with integration test at `date`"



