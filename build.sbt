name := "Akka Examples"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= {
  val akkaV  = "2.4.0"

  Seq(
      "com.typesafe.akka"           %% "akka-actor"     % akkaV
    , "com.typesafe.akka"           %% "akka-testkit"   % akkaV
    , "com.typesafe.scala-logging"  %% "scala-logging"  % "3.1.0"
    , "org.scalatest"               %% "scalatest"      % "2.2.4" % "test"
  )
}
