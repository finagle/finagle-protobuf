# Finagle Protobuf

This is an extraction of the finagle-protobuf stuff from Tendril's fork of finagle.

See the [Finagle-Protobuf README](finagle-protobuf/README.md) for more details on the protocol itself.

# Releases

| Version | Notes |
| ------- | ----- |
| 9.0.0   | Finagle 6.42 and Scala 2.11.11 |

# Project setup

Finagle is built using [sbt](https://github.com/sbt/sbt). Simply use the latest version of sbt (0.13.6). To build:

```sh
$ sbt test
```

# Publishing

We're publishing to the [Tendril Artifactory](https://tendril.artifactoryonline.com). To publish your local version there:

```sh
$ sbt publish
```

The better way to push your version to Artifactory, is to let Jenkins do it. Jenkins will automatically build commits on
master and publish them as part of the build.

To publish to your local repos:

```sh
$ # ~/.ivy2
$ sbt publish-local
$
$ # ~/.m2
$ sbt publish-m2
```

To perform a release:

1. Update the version, commit, tag and push
        
        $ vi project/Build.scala
          object FinagleProtobuf extends Build {

          val libVersion = "8.1.1-SNAPSHOT"  <----- CHANGE THIS (e.g. 8.1.1)

        $ git commit -am "Releasing finagle-protobuf-8.1.1"
        $ git tag finagle-protobuf-8.1.1
        $ git push origin master
      
2. Kick off the [build in Jenkins](https://jenkins.useast.tni01.com:8443/job/Platform_Services_Dependencies/job/finagle-protobuf/)
 ( sbt will automatically deploy to the release repo since the version does not contain `SNAPSHOT` ) This build should
 trigger automatically when you pushed the update above.
3. Update the version to the next `SNAPSHOT`
      
        $ vi project/Build.scala
          object FinagleProtobuf extends Build {

          val libVersion = "8.1.1"  <----- CHANGE THIS (e.g. 8.1.2-SNAPSHOT)
        $ git commit -am "Bumping to 8.1.2-SNAPSHOT after release of finagle-protobuf-8.1.1"
        $ git push origin master


# Usage

> TODO: insert a finagle-protobuf service/client example
