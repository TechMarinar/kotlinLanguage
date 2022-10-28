#!/bin/bash

#
# Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
# Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
#

# **Warning** Don't execute the script or any part of it till you verify it is safe for your environment.

# Script for building reproducible-maven.zip from sources. This is a full set of artefacts published to maven central during
# the Kotlin release process.

# In order to re-execute the script for the same set of parameters, docker container and directory with
# the repository should be removed first.
#   rm -rf -I kotlin-build-$DEPLOY_VERSION
#   docker container rm kotlin-build-$DEPLOY_VERSION

set -e

if [ $# -lt 5 ]; then
    echo "Not enough arguments provided. Usage: $0 REF DEPLOY_VERSION BUILD_NUMBER DOCKER_CONTAINER_URL KOTLIN_NATIVE_VERSION"
    exit 1
fi

REF=$1
DEPLOY_VERSION=$2
BUILD_NUMBER=$3
DOCKER_CONTAINER_URL=$4
KOTLIN_NATIVE_VERSION=$5

KOTLIN_REPO_DIR=$6 # Optional parameter for using existing checkout
KOTLIN_DOCKER_CONTAINER=$7 # Optional parameter for re-using existing docker container

echo "REF=$REF"
echo "DEPLOY_VERSION=$DEPLOY_VERSION"
echo "BUILD_NUMBER=$BUILD_NUMBER"
echo "DOCKER_CONTAINER_URL=$DOCKER_CONTAINER_URL"
echo "KOTLIN_NATIVE_VERSION=$KOTLIN_NATIVE_VERSION"

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

# Update versions in pom.xml
docker exec kotlin-build-$DEPLOY_VERSION \
  mvn -DnewVersion=$DEPLOY_VERSION -DgenerateBackupPoms=false -DprocessAllModules=true -f libraries/pom.xml versions:set

# Build part of kotlin and publish it to the local maven repository. Publishing is actually done inside the docker container.
docker exec $KOTLIN_DOCKER_CONTAINER \
  ./gradlew \
    -PdeployVersion=$DEPLOY_VERSION \
    -Pbuild.number=$BUILD_NUMBER \
    -Pversions.kotlin-native=$KOTLIN_NATIVE_VERSION \
    -Pteamcity=true \
    install publish

# Additionally publish same artefacts to a dedicated directory to collect the archive later
docker exec $KOTLIN_DOCKER_CONTAINER \
  ./gradlew \
    -PdeployVersion=$DEPLOY_VERSION \
    -Pbuild.number=$BUILD_NUMBER \
    -Pversions.kotlin-native=$KOTLIN_NATIVE_VERSION \
    -Pteamcity=true \
    -PdeployRepo=local \
    -PdeployRepoUrl=file:///repo/build/maven_local \
    install publish

# Build maven part and publish it to the same directory
docker exec $KOTLIN_DOCKER_CONTAINER \
  mvn \
    -f libraries/pom.xml \
    clean deploy \
    -Ddeploy-url=file:///repo/build/maven_local \
    -DskipTests

# Prepare for reproducibility check
docker exec $KOTLIN_DOCKER_CONTAINER mkdir -p /repo/build/maven_reproducable
docker exec $KOTLIN_DOCKER_CONTAINER cp -R /repo/build/maven_local/. /repo/build/maven_reproducable
# maven-metadata contains lastUpdated section with the build time
docker exec $KOTLIN_DOCKER_CONTAINER sh -c 'find /repo/build/maven_reproducable -name "maven-metadata.xml*" -exec rm -rf {} \;'
# Each file has own timestamp that would affect zip file hash if not aligned
docker exec $KOTLIN_DOCKER_CONTAINER sh -c 'find /repo/build/maven_reproducable -exec touch -t "198001010000" {} \;'
docker exec $KOTLIN_DOCKER_CONTAINER "cd /repo/build/maven_reproducable && zip -rX -9 /repo/reproducible-maven-$DEPLOY_VERSION.zip . && cd -"
