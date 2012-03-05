#!/bin/bash

# script that launches a myberkeley instance and runs its suite
# of integration tests.

PORT="8899"

stopAndExit() {
  mvn -P runner -Dsling.stop -Dsling.port=$PORT verify;
  exit 1;
}

echo "Starting myberkeley integration test run at `date`"

# stop the server and clean it out
mvn -P runner -Dsling.stop -Dsling.port=$PORT verify
mvn -Dsling.clean clean || { echo "Sling clean failed." ; stopAndExit ; }

# reinstall
mvn clean install || { echo "mvn clean install failed." ; stopAndExit ; }

# make the server think its port is safe
mkdir -p working/load
echo "trusted.hosts=[\"localhost:$PORT\\ \\=\\ http://localhost:8082\"]" > working/load/org.sakaiproject.nakamura.http.usercontent.ServerProtectionServiceImpl.config

# start up
mvn -P runner -Dsling.start -Dsling.port=$PORT verify || { echo "Failed to start sling." ; stopAndExit ; }

# wait so sling can get going
echo "Waiting for startup..."
MAXTRIES=10
TRIES=0
LOGFILE=working/sling/logs/error.log
while ! [ $TRIES -gt $MAXTRIES ] && ! [ -e $LOGFILE ] ;do sleep 30; TRIES=$((TRIES+1)); done
while ! [ $TRIES -gt $MAXTRIES ] && ! grep "org.sakaiproject.nakamura.world BundleEvent STARTED" $LOGFILE > /dev/null  2>&1;do sleep 30; TRIES=$((TRIES+1)); done
if [ $TRIES -gt $MAXTRIES ]
then
    echo "Startup did not complete for a long time. Giving up."
    exit 1
fi

# run myberkeley dataloader
mvn -e -P runner -Dsling.loaddata -Dsling.port=$PORT -Dloaddata.server=http://localhost:$PORT/ -Dloaddata.password=admin integration-test || { echo "Integration-test failed." ; stopAndExit ; }

# stop server
mvn -P runner -Dsling.stop -Dsling.port=$PORT verify

echo "Done with integration test at `date`"



