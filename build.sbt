organization in ThisBuild := "com.okune"

scalaVersion in ThisBuild := "2.12.3"

val akkaV = "2.5.4"

scalacOptions in ThisBuild :=
  Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-unchecked",
    "-Xfuture",
    "-Xlint",
    "-Xmax-classfile-name", "254",
    "-Ywarn-unused-import")

javacOptions in ThisBuild ++=
  Seq(
    "-source", "1.8",
    "-target", "1.8")

lazy val root =
  (project in file("."))
    .aggregate(
      streams)

lazy val streams =
  Project(id = "akka-streams", base = file("akka-streams"))
    .settings(
      libraryDependencies ++=
        Seq("com.typesafe.akka" %% "akka-stream" % akkaV),
      fork in Test := true)
