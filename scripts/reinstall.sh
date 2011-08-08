#!/bin/bash

# script to reinstall myberkeley on calcentral-dev/calcentral-qa, while preserving content repository

if [ -z "$1" ]; then
    echo "Usage: $0 source_root logfile"
    exit;
fi

SRC_LOC=$1

INPUT_FILE="$SRC_LOC/.build.cf"
if [ -f $INPUT_FILE ]; then
  SLING_PASSWORD=`awk -F"=" '/APPLICATION_PASSWORD/ {print $2}' $INPUT_FILE`
  SHARED_SECRET=`awk -F"=" '/SHARED_SECRET/ {print $2}' $INPUT_FILE`
  CONFIG_FILE_DIR=`awk -F"=" '/CONFIG_FILE_DIR/ {print $2}' $INPUT_FILE`
else
  SLING_PASSWORD='admin'
  SHARED_SECRET='SHARED_SECRET_CHANGE_ME_IN_PRODUCTION'
  CONFIG_FILE_DIR="localhost"
fi

CONFIG_FILES="$SRC_LOC/myberkeley/configs/$CONFIG_FILE_DIR/load"

LOG=$2
if [ -z "$2" ]; then
    LOG=/dev/null
fi
LOGIT="tee -a $LOG"

echo "=========================================" | $LOGIT
echo "`date`: Update started" | $LOGIT

echo | $LOGIT

cd $SRC_LOC/myberkeley
echo "`date`: Stopping sling..." | $LOGIT
mvn -B -e -Dsling.stop -P runner verify >>$LOG 2>&1 | $LOGIT
echo "`date`: Cleaning sling directories..." | $LOGIT
mvn -B -e -P runner -Dsling.purge clean >>$LOG 2>&1 | $LOGIT
rm -rf ~/.m2/repository/edu/berkeley

echo "`date`: Fetching new sources for myberkeley..." | $LOGIT
git pull >>$LOG 2>&1
echo "Last commit:" | $LOGIT
git log -1 | $LOGIT
echo | $LOGIT
echo "------------------------------------------" | $LOGIT

echo "`date`: Fetching new sources for 3akai-ux..." | $LOGIT
cd ../3akai-ux
git pull >>$LOG 2>&1
echo "Last commit:" | $LOGIT
git log -1 | $LOGIT
echo | $LOGIT
echo "------------------------------------------" | $LOGIT

cd ../myberkeley

echo "Updating local configuration files..." | $LOGIT

# put the shared secret into config file
SERVER_PROT_CFG=$CONFIG_FILES/org.sakaiproject.nakamura.http.usercontent.ServerProtectionServiceImpl.config
if [ -f $SERVER_PROT_CFG ]; then
  grep -v trusted\.secret= $SERVER_PROT_CFG > $SERVER_PROT_CFG.new
  echo "trusted.secret=\"$SHARED_SECRET\"" >> $SERVER_PROT_CFG.new
  mv -f $SERVER_PROT_CFG.new $SERVER_PROT_CFG
fi

rm $SRC_LOC/myberkeley/working/load/*
cp -f $CONFIG_FILES/* $SRC_LOC/myberkeley/working/load

echo "`date`: Doing clean install..." | $LOGIT
mvn -B -e clean install >>$LOG 2>&1

echo "`date`: Starting sling..." | $LOGIT
mvn -B -e -Dsling.start -P runner verify >>$LOG 2>&1

# wait 2 minutes so sling can get going
sleep 120;

echo "`date`: Redeploying UX..." | $LOGIT
cd ../3akai-ux
mvn clean install -P sakai-release
mvn -B -e -Dsling.user=admin -Dsling.password=$SLING_PASSWORD org.apache.sling:maven-sling-plugin:install-file \
  -Dsling.file=./target/org.sakaiproject.nakamura.uxloader-myberkeley-0.7-SNAPSHOT.jar

echo | $LOGIT
echo "`date`: Reinstall complete." | $LOGIT

