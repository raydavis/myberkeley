#!/bin/bash

if [ -z "$1" ]; then
    echo "Usage: $0 their_git_url their_branch"
    exit;
fi

THEIR_GIT_URL=$1
THEIR_BRANCH=$2
REMOTE_NAME="theirRepo"

fail() {
  git remote rm $REMOTE_NAME
  git reset --hard HEAD
  exit 1;
}

git remote add $REMOTE_NAME $THEIR_GIT_URL
git fetch $REMOTE_NAME

git merge $REMOTE_NAME/$THEIR_BRANCH || { echo "Automatic merge failed. Resetting to HEAD."; fail; }

git remote rm $REMOTE_NAME






