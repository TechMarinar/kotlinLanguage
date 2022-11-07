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
export DOCKER_CONTAINER_URL=kotlin.registry.jetbrains.space/p/kotlin/containers/kotlin-build-env:v5
```

Checkout Kotlin repository to the clean folder from the release tag:

```
git clone --depth 1 --branch v$DEPLOY_VERSION https://github.com/JetBrains/kotlin.git kotlin-build-$DEPLOY_VERSION
cd kotlin-build-$DEPLOY_VERSION
```

## kotlin-compiler.zip

Copy `scripts/build-kotlin-compiler.sh` from the 1.7.20 branch to `scripts` directory in the repo (this script wasn't present in the 
repository at the moment of the v1.7.20 tag creation) and execute it in the docker container:

```
docker run --rm -it --name kotlin-build-$DEPLOY_VERSION \
  --workdir="/repo" --volume="$(pwd):/repo" $DOCKER_CONTAINER_URL \
  /bin/bash -c "./scripts/build-kotlin-compiler.sh $DEPLOY_VERSION $BUILD_NUMBER"
```

Output file is `dist/kotlin-compiler-$DEPLOY_VERSION.zip`.
Check that SHA 256 checksum is the same to one published on GitHub.

[kotlin-compiler-1.7.20.zip](https://github.com/JetBrains/kotlin/releases/download/v1.7.20/kotlin-compiler-1.7.20.zip) - 5e3c8d0f965410ff12e90d6f8dc5df2fc09fd595a684d514616851ce7e94ae7d

## Maven artifact

Copy `scripts/build-kotlin-maven.sh` from the 1.7.20 branch to `scripts` directory in the repo (this script wasn't present in the
repository at the moment of the v1.7.20 tag creation) and execute it in the docker container:

```
docker run --rm -it --name kotlin-build-$DEPLOY_VERSION \
  --workdir="/repo" --volume="$(pwd):/repo" $DOCKER_CONTAINER_URL \
  /bin/bash -c "./scripts/build-kotlin-maven.sh $DEPLOY_VERSION $BUILD_NUMBER $KOTLIN_NATIVE_VERSION"
```

Output file is `build/maven_reproducible/reproducible-maven-$DEPLOY_VERSION.zip`.

**Note:** Instructions for checking reproducibility will be covered in the upcoming release. Yet many jars published to maven
central are already reproducible with this tutorial.

## Kotlin Native artifacts

This tutorial will be finished later. 
