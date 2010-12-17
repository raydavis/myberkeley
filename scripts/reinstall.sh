#!/bin/bash

# script to reinstall myberkeley on portal-dev/portal-qa, while preserving content repository

if [ -z "$1" ]; then
    echo "Usage: $0 source_root sling_password"
    exit;
fi

SRC_LOC=$1
SLING_PASSWORD=$2

echo "Update started at `date`"

echo

cd $SRC_LOC/myberkeley
echo "Stopping sling..."
mvn -q -Dsling.stop -P runner verify &>/dev/null

echo "Cleaning sling directories..."
mvn -q -P runner -Dsling.purge clean &>/dev/null

echo "Fetching new sources for myberkeley..."
git pull &>/dev/null
echo "Last commit:"
git log -1
echo
echo "------------------------------------------"

echo "Fetching new sources for 3akai-ux..."
cd ../3akai-ux
git pull &>/dev/null
echo "Last commit:"
git log -1
echo
echo "------------------------------------------"

cd ../myberkeley

echo "Doing clean install..."
mvn -q clean install &>/dev/null

echo "Starting sling..."
mvn -q -Dsling.start -P runner verify 

echo "Redeploying UX..."
cd ../3akai-ux
mvn -q -P redeploy -Dsling.user=admin -Dsling.password=$SLING_PASSWORD &>/dev/null

echo

echo "All done at `date`"

