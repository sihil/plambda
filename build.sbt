organization := "net.sihil"

name := "plambda"
description := "Wrapper for running Play! apps in AWS Lambda"

scalaVersion in ThisProject := "2.11.8"

val playVersion = "2.5.0"
val awsSdkVersion = "1.11.0"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % playVersion % "provided",
  "com.typesafe.play" %% "play-json" % playVersion % "provided",
  "com.typesafe.play" %% "play-test" % playVersion % "provided",
  "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion % "provided",
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
  "com.amazonaws" % "aws-lambda-java-events" % "1.1.0" intransitive(),
  "com.amazonaws" % "aws-lambda-java-log4j" % "1.0.0"
)

publishMavenStyle := true
bintrayOrganization := Some("sihil")
bintrayRepository := "plambda"
licenses += ("Apache-2.0", url("https://github.com/guardian/tags-thrift-schema/blob/master/LICENSE"))

// Release
import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  releaseStepTask(bintrayRelease),
  setNextVersion,
  commitNextVersion,
  pushChanges
)