#!/bin/bash

#
# Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
# Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
#

# **Warning** Don't execute the script or any part of it till you verify it is safe for your environment.

# Script for building kotlin-compiler.zip from sources.

# In order to re-execute the script for the same set of parameters, docker container and directory with
# the repository should be removed first.
#   rm -rf -I kotlin-build-$DEPLOY_VERSION
#   docker container rm kotlin-build-$DEPLOY_VERSION

set -e

if [ $# -lt 4 ]; then
    echo "Not enough arguments provided. Usage: $0 REF DEPLOY_VERSION BUILD_NUMBER DOCKER_CONTAINER_URL"
    exit 1
fi

REF=$1
DEPLOY_VERSION=$2
BUILD_NUMBER=$3
DOCKER_CONTAINER_URL=$4

KOTLIN_REPO_DIR=$5 # Optional parameter for using existing checkout
KOTLIN_DOCKER_CONTAINER=$6 # Optional parameter for re-using existing docker container

echo "REF=$REF"
echo "DEPLOY_VERSION=$DEPLOY_VERSION"
echo "BUILD_NUMBER=$BUILD_NUMBER"
echo "DOCKER_CONTAINER_URL=$DOCKER_CONTAINER_URL"

# Check docker is active before start
docker ps -q

if [[ -z "${KOTLIN_REPO_DIR}" ]]; then
  KOTLIN_REPO_DIR=kotlin-build-$DEPLOY_VERSION
  git clone --depth 1 --branch v$DEPLOY_VERSION https://github.com/JetBrains/kotlin.git $KOTLIN_REPO_DIR
fi
echo "KOTLIN_REPO_DIR=$KOTLIN_REPO_DIR"

cd $KOTLIN_REPO_DIR

# Create docker container
if [[ -z "${KOTLIN_DOCKER_CONTAINER}" ]]; then
  KOTLIN_DOCKER_CONTAINER=kotlin-build-$DEPLOY_VERSION
  docker run --name $KOTLIN_DOCKER_CONTAINER --workdir="/repo" --volume="$(pwd):/repo" -tid $DOCKER_CONTAINER_URL
fi
echo "KOTLIN_DOCKER_CONTAINER=$KOTLIN_DOCKER_CONTAINER"

# Build dist/kotlin-compiler.zip
docker exec kotlin-build-$DEPLOY_VERSION ./gradlew -PdeployVersion=$DEPLOY_VERSION -Pbuild.number=$BUILD_NUMBER -Pteamcity=true zipCompiler -Dfile.encoding=UTF-8