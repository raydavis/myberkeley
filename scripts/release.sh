#!/bin/bash

set -o nounset
set -o errexit
source_root=$1
cversion=$2
nversion=$3
tag=$4

echo "Creating tagged version: $nversion-$tag"

cd $source_root

listofpoms=`find . -name pom.xml -exec grep -l SNAPSHOT {} \;| egrep -v ".git|do_release.sh|target|binary/release|uxloader/src/main/resources|last-release|cachedir"`
  listofpomswithversion=`grep -l $cversion-SNAPSHOT $listofpoms`

echo "Creating Release"
for i in $listofpomswithversion
do
  sed "s/$cversion-SNAPSHOT/$nversion-$tag/" $i > $i.new
  mv $i.new $i
done

echo "Remaining SNAPSHOT versions in the release"
echo "=================================================="
grep -C5 SNAPSHOT $listofpoms
echo "=================================================="





