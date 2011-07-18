#!/bin/bash

set -o nounset
set -o errexit
source_root=$1

nakamura_version=$2
ian_version=$3
ux_version=$4

tag=$5

cd $source_root
listofpoms=`find . -name pom.xml -exec grep -l SNAPSHOT {} \;| egrep -v ".git|do_release.sh|target|binary/release|uxloader/src/main/resources|last-release|cachedir"`

echo "-----------------------------------------------------------"
echo "Creating tagged version of nakamura: $nakamura_version-$tag"
listofpomswithversion=`grep -l $nakamura_version-SNAPSHOT $listofpoms`
for i in $listofpomswithversion
do
  sed "s/$nakamura_version-SNAPSHOT/$nakamura_version-$tag/" $i > $i.new
  mv $i.new $i
done

echo "-----------------------------------------------------------"
echo "Creating tagged version of sparsemap and solr: $ian_version-$tag"
listofpomswithversion=`grep -l $ian_version-SNAPSHOT $listofpoms`
for i in $listofpomswithversion
do
  sed "s/$ian_version-SNAPSHOT/$ian_version-$tag/" $i > $i.new
  mv $i.new $i
done

echo "-----------------------------------------------------------"
echo "Creating tagged version of ux: $ux_version-$tag"
listofpomswithversion=`grep -l $ux_version-SNAPSHOT $listofpoms`
for i in $listofpomswithversion
do
  sed "s/$ux_version-SNAPSHOT/$ux_version-$tag/" $i > $i.new
  mv $i.new $i
done

echo "-----------------------------------------------------------"

echo "Remaining SNAPSHOT versions in the release"
echo "=================================================="
grep -C5 SNAPSHOT $listofpoms
echo "=================================================="





