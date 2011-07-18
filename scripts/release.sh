#!/bin/bash

# This script will change -SNAPSHOT version numbers to quasi-release versions for myberkeley and all its
# constituent projects (nakamura, solr, sparse, 3akai-ux). It won't do any git work for you.

# syntax:
# ./release.sh SOURCE_DIR NAKAMURA_VERSION IAN_VERSION UX_VERSION TAG

# example:
# ./release.sh ~/merge/0.3 0.11 1.0 0.7 20110718TEST

set -o nounset
set -o errexit
source_root=$1

nakamura_version=$2
ian_version=$3
ux_version=$4

tag=$5

cd $source_root/nakamura
listofpoms=`find . -name pom.xml -or -name list.xml -exec grep -l SNAPSHOT {} \;| egrep -v ".git|do_release.sh|target|binary/release|uxloader/src/main/resources|last-release|cachedir"`

echo "-----------------------------------------------------------"
echo "Creating tagged version of nakamura: $nakamura_version-$tag"
listofpomswithversion=`grep -l $nakamura_version-SNAPSHOT $listofpoms`
for i in $listofpomswithversion
do
  sed "s/$nakamura_version-SNAPSHOT/$nakamura_version-$tag/" $i > $i.new
  mv $i.new $i
done

cd $source_root
listofpoms=`find . -name pom.xml -exec grep -l SNAPSHOT {} \;| egrep -v "nakamura|.git|do_release.sh|target|binary/release|uxloader/src/main/resources|last-release|cachedir"`

echo "-----------------------------------------------------------"
echo "Creating tagged version of sparsemap and solr: $ian_version-$tag"
listofpomswithversion=`grep -l $ian_version-SNAPSHOT $listofpoms`
for i in $listofpomswithversion
do
  sed "s/$ian_version-SNAPSHOT/$ian_version-$tag/" $i > $i.new
  mv $i.new $i
done

cd $source_root/nakamura
listxml=`find . -name list.xml -exec grep -l SNAPSHOT {} \;| egrep -v "nakamura|.git|do_release.sh|target|binary/release|uxloader/src/main/resources|last-release|cachedir"`

echo "-----------------------------------------------------------"
echo "Updating list.xml with new versions of sparsemap and solr: $ian_version-$tag"
listxmlwithversion=`grep -l $ian_version-SNAPSHOT $listxml`
for i in $listxmlwithversion
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





