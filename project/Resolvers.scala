import sbt._

object Resolvers {
  val tendrilArtifactory = "Artifactory" at "https://tendril.jfrog.io/artifactory/libs-release"
  val tendrilArtifactoryReleases = "Artifactory Release" at "https://tendril.jfrog.io/artifactory/libs-release-local"
  val tendrilArtifactorySnapshots = "Artifactory snapshots" at "https://tendril.jfrog.io/artifactory/libs-snapshot-local;build.timestamp=" + new java.util.Date().getTime
}