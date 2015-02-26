name := "Akka Examples"

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT",
  "com.typesafe.akka" %% "akka-testkit" % "2.4-SNAPSHOT",
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
)

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
