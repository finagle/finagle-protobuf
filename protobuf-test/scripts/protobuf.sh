#!/bin/sh

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION=6T1.13.1T1-TENDRIL-SNAPSHOT
echo "version: $VERSION"

java -jar $DIR/../target/scala-2.9.2/protobuf-test-assembly-$VERSION.jar $*
