#!/bin/bash

if [ -z "$1" ]; then
    echo "Usage: $0 our_git_url our_branch"
    exit;
fi

OUR_GIT_URL=$1
OUR_BRANCH=$2
REMOTE_NAME="ourRepo"

fail() {
  git remote rm $REMOTE_NAME
  exit 1;
}

git remote add $REMOTE_NAME $OUR_GIT_URL
git push $REMOTE_NAME $OUR_BRANCH:$OUR_BRANCH || { echo "Git push failed."; fail; }
git remote rm $REMOTE_NAME

