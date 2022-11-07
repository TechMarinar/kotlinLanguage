#!/bin/bash

#
# Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
# Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
#

# **Warning** Don't execute the script or any part of it till you verify it is safe for your environment.

# Script for building reproducible-maven.zip from sources. This is a full set of artifacts published to maven central during
# the Kotlin release process.

# Run the script in the root Kotlin directory.

set -e

if [ $# -lt 3 ]; then
    echo "Not enough arguments provided. Usage: $0 DEPLOY_VERSION BUILD_NUMBER KOTLIN_NATIVE_VERSION"
    exit 1
fi

DEPLOY_VERSION=$1
BUILD_NUMBER=$2
KOTLIN_NATIVE_VERSION=$3

echo "DEPLOY_VERSION=$DEPLOY_VERSION"
echo "BUILD_NUMBER=$BUILD_NUMBER"
echo "KOTLIN_NATIVE_VERSION=$KOTLIN_NATIVE_VERSION"

# Update versions in pom.xml
mvn -DnewVersion=$DEPLOY_VERSION -DgenerateBackupPoms=false -DprocessAllModules=true -f libraries/pom.xml versions:set

# Build part of kotlin and publish it to the local maven repository
./gradlew \
  -PdeployVersion=$DEPLOY_VERSION \
  -Pbuild.number=$BUILD_NUMBER \
  -Pversions.kotlin-native=$KOTLIN_NATIVE_VERSION \
  -Pteamcity=true \
  install publish

# Additionally publish same artifacts to a dedicated directory to collect the archive later
./gradlew \
  -PdeployVersion=$DEPLOY_VERSION \
  -Pbuild.number=$BUILD_NUMBER \
  -Pversions.kotlin-native=$KOTLIN_NATIVE_VERSION \
  -Pteamcity=true \
  -PdeployRepo=local \
  -PdeployRepoUrl=file://$(pwd)/build/maven_local \
  install publish

# Build maven part and publish it to the same directory
mvn \
  -f libraries/pom.xml \
  clean deploy \
  -Ddeploy-url=file://$(pwd)/build/maven_local \
  -DskipTests

# Prepare for reproducibility check
mkdir -p build/maven_reproducible
cp -R build/maven_local/. build/maven_reproducible
# maven-metadata contains lastUpdated section with the build time
find build/maven_reproducible -name "maven-metadata.xml*" -exec rm -rf {} \;
# Each file has own timestamp that would affect zip file hash if not aligned
find build/maven_reproducible -exec touch -t "198001010000" {} \;
cd build/maven_reproducible && zip -rX -9 reproducible-maven-$DEPLOY_VERSION.zip . && cd -
