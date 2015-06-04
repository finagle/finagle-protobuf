lazy val buildSettings = Seq(
  organization := "com.twitter",
  version := "6.26.0-SNAPSHOT",
  scalaVersion := "2.11.6",
  crossScalaVersions := Seq("2.10.5", "2.11.6"),
  libraryDependencies ++= Seq(
    "com.google.protobuf" % "protobuf-java" % "2.4.1",
    "com.twitter" %% "finagle-core" % "6.25.0",
    "junit" % "junit" % "4.12" % "test",
    "org.specs2" %% "specs2-core" % "2.4.17" % "test",
    "org.specs2" %% "specs2-junit" % "2.4.17" % "test",
    "org.slf4j" % "slf4j-nop" % "1.7.12"
  )
)

lazy val root = project.in(file("."))
  .settings(moduleName := "finagle-protobuf")
  .settings(buildSettings)
