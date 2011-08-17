#!/bin/bash

# script to restore production data onto your sling instance.
# this DESTROYS the content repository!
# more details at:
# https://confluence.media.berkeley.edu/confluence/display/MYB/Migrating+Production+Data+to+new+Sling+instance

if [ -z "$1" ]; then
    echo "Usage: $0 source_root sling_password mysql_dump_location jackrabbit_tar_location"
    exit;
fi

SRC_LOC=$1
SLING_PASSWORD=$2
MYSQL_DUMP=$3
JACKRABBIT_TAR=$4

MYSQL="mysql -p$SLING_PASSWORD -u sakaiuser"

cd $SRC_LOC/myberkeley

mvn -P runner -Dsling.stop verify

mvn -P runner -Dsling.clean clean

echo "drop database nakamura;" | $MYSQL

echo "create database nakamura default character set 'utf8';" | $MYSQL

mvn -P runner -Dsling.start -Dmyb.sling.config=$SRC_LOC/scripts/mysql verify

mvn -P runner -Dsling.stop verify

echo "drop database nakamura;" | $MYSQL

echo "create database nakamura default character set 'utf8';" | $MYSQL

gzip -cd $MYSQL_DUMP | $MYSQL nakamura

gzip -cd $JACKRABBIT_TAR | tar oxv

./scripts/reinstall.sh $SRC_LOC $SLING_PASSWORD

