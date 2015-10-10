name := "Akka Examples"

version := "1.0"

scalaVersion := "2.11.7"

val AkkaVersion = "2.4.0"
val ScalaTestVersion = "2.2.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
  "org.scalatest" % "scalatest_2.11" % ScalaTestVersion % "test"
)

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
