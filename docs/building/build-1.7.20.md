# 1.7.20

Instructions for building [1.7.20](https://github.com/JetBrains/kotlin/releases/tag/v1.7.20) release locally.

Linux or MacOS machine with the Docker is required. Allow to use 14GB Memory in the Docker settings. 

**Warning** Don't execute commands and scripts from this tutorial till you fully understand what they do and
verify they are safe for your environment.

## Common

Following steps expect several variables:

```
export DEPLOY_VERSION=1.7.20
export BUILD_NUMBER=1.7.20-release-201
export KOTLIN_NATIVE_VERSION=1.7.20
export REF=7159702
export DOCKER_CONTAINER_URL=kotlin.registry.jetbrains.space/p/kotlin/containers/kotlin-build-env:v5
```

## kotlin-compiler.zip

Copy scripts/kotlin-compiler.sh from the repo to some dedicated empty directory and execute it:

```
./kotlin-compiler.sh $REF $DEPLOY_VERSION $BUILD_NUMBER $DOCKER_CONTAINER_URL
```

Output file is `kotlin-build-$DEPLOY_VERSION/dist/kotlin-compiler-$DEPLOY_VERSION.zip`.
Check that SHA 256 checksum is the same to one published on GitHub.

[kotlin-compiler-1.7.20.zip](https://github.com/JetBrains/kotlin/releases/download/v1.7.20/kotlin-compiler-1.7.20.zip) - 5e3c8d0f965410ff12e90d6f8dc5df2fc09fd595a684d514616851ce7e94ae7d

## Maven artefact

Copy scripts/kotlin-compiler.sh from the repo to some dedicated empty directory and execute the command:

```
./kotlin-maven.sh $REF $DEPLOY_VERSION $BUILD_NUMBER $DOCKER_CONTAINER_URL $KOTLIN_NATIVE_VERSIO 
```

Output file is `reproducible-maven-$DEPLOY_VERSION.zip`.

**Note:** Instructions for checking reproducibility will be covered in the upcoming release. Yet many jars published to maven
central are already reproducible with this tutorial.

## Kotlin Native artefacts

This tutorial will be finished later. 
