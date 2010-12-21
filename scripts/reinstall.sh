#!/bin/bash

# script to reinstall myberkeley on portal-dev/portal-qa, while preserving content repository

if [ -z "$1" ]; then
    echo "Usage: $0 source_root sling_password logfile"
    exit;
fi

SRC_LOC=$1
SLING_PASSWORD=$2
LOG=$3

if [ -z "$3" ]; then
    LOG=/dev/null
fi

LOGIT="tee -a $LOG"

echo "=========================================" | $LOGIT
echo "`date`: Update started" | $LOGIT

echo | $LOGIT

cd $SRC_LOC/myberkeley
echo "`date`: Stopping sling..." | $LOGIT
mvn -q -e -Dsling.stop -P runner verify >>$LOG 2>&1 | $LOGIT
echo "`date`: Cleaning sling directories..." | $LOGIT
mvn -q -e -P runner -Dsling.purge clean >>$LOG 2>&1 | $LOGIT

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

echo "`date`: Doing clean install..." | $LOGIT
mvn -q -e clean install >>$LOG 2>&1 

echo "`date`: Starting sling..." | $LOGIT
mvn -q -e -Dsling.start -P runner verify >>$LOG 2>&1

echo "`date`: Redeploying UX..." | $LOGIT
cd ../3akai-ux
mvn -q -e -P redeploy -Dsling.user=admin -Dsling.password=$SLING_PASSWORD >>$LOG 2>&1

echo | $LOGIT
echo "`date`: Reinstall complete." | $LOGIT

