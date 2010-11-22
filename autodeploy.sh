#!/bin/bash

REPOS_XML=integration/src/main/resources/repository.xml

if [ -z "$1" ]; then
    echo Usage: $0 db_password
    exit
fi    

echo "Stopping Sling..."
mvn -Dsling.stop -P runner verify

echo "Updating source code from git repository"
git pull

# update the db password in /myberkeley/integration/src/main/resources/repository.xml
git checkout -- $REPOS_XML
cp $REPOS_XML $REPOS_XML.orig
sed 's/<param name="password" value="ironchef" \/>/<param name="password" value="'$1'" \/>/g' $REPOS_XML.orig > $REPOS_XML

# bounce apache
echo "Restarting apache"
/etc/init.d/httpd_proxy restart

echo "Starting Sling..."
mvn -P runner -Dsling.start -Dsling.include.nakamura=/home/myberkeley/myberkeley/launchpad/src/main/resources/nakamura.properties verify


