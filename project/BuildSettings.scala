import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

object BuildSettings {
  lazy val releaseSettings = Seq(
    publishTo := Some(if (isSnapshot.value) Resolvers.tendrilArtifactorySnapshots else Resolvers.tendrilArtifactoryReleases),
    releaseIgnoreUntrackedFiles := true,
    //defines release steps
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

}
