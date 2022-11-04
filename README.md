# Finagle Protobuf

[![Build status](https://circleci.com/gh/Uplight-Inc/finagle-protobuf/tree/DIG-1843_Resurrectw212.svg?style=svg)](https://circleci.com/gh/Uplight-Inc/finagle-protobuf/?branch=DIG-1843_Resurrectw212)

This repository is the new home of `Uplight-Inc/finagle-protobuf`, which used to be a [Finagle][finagle] subproject.
`finagle-protobuf` provides abstractions to enable RPC functionality using Protobuf as a codec between distributed nodes.
As the name suggests, it is intended to be used in conjunction with the `finagle` RPC framework.

## Usage

This library is typically used alongside [service-deps-rpc](https://github.com/tendrilinc/platform-services-deps/tree/master/service-deps-rpc).
The main abstaction that is `com.twitter.finagle.protobuf.rpc.RpcFactory` which provides the ability to define RpcServer or a finagle client
of any defined RpcServer. As one might expect, any service utilizing the RpcServer type inherently uses the
`com.twitter.finagle.protobuf.rpc.channel.ProtobufCodec` to encode/decode transmissions.

## Building

This project is built using 1.7.2. of [sbt](https://www.scala-sbt.org/download.html).

### Locally
To build this you can run:
```sbt test package```
This will run the unit tests and create a jar in the local `target` directory.

### Locally using the CircleCI CLI

There is a script provided on this repo that allows engineers to
use a CircleCI docker image and the CircleCI CLI to run the build.
The script can be invoked by running the script in the root of this
project: `./circleci_runner.sh`. This script assumes you have Docker
(or Rancher Desktop) installed locally and you can run docker images.
It also assumes you're running this in macOS.

## In CircleCI
There are two workflows that are defined in this project's build.

### feature-branch-build
The `feature-branch-build` is triggered when a non-master branch is pushed
to the remote repo. This workflow runs unit tests and publishes a SNAPSHOT
to JFrog for integration testing in DEV-US.

### master-build
The `master-build` is triggered when a push to the master branch is detected.
This workflow runs unit tests, performs the release and publish to JFrog
tasks.

## License

Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
