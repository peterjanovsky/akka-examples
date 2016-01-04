import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "Akka Examples"

version := "1.0"

scalaVersion := "2.11.7"

val root = (project in file(".")).configs(MultiJvm)

libraryDependencies ++= {

  val AkkaVersion = "2.4.1"

  Seq(
      "ch.qos.logback"    %   "logback-classic"         % "1.1.3"
    , "com.typesafe.akka" %%  "akka-actor"              % AkkaVersion
    , "com.typesafe.akka" %%  "akka-cluster"            % AkkaVersion
    , "com.typesafe.akka" %%  "akka-cluster-metrics"    % AkkaVersion
    , "com.typesafe.akka" %%  "akka-slf4j"              % AkkaVersion
    , "com.typesafe.akka" %%  "akka-testkit"            % AkkaVersion
    , "com.typesafe.akka" %%  "akka-multi-node-testkit" % AkkaVersion
    , "org.scalatest"     %%  "scalatest"               % "2.2.4" % "test,multi-jvm"
  ) }

compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test)

javaOptions in MultiJvm ++= Seq(
  "-Dconfig.file=src/multi-jvm/resources/application.conf",
  "-Dlogback.configurationFile=src/multi-jvm/resources/logback-test.xml")

parallelExecution in Test := false

executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
  case (testResults, multiNodeResults) =>
    val overall =
      if (testResults.overall.id < multiNodeResults.overall.id)
        multiNodeResults.overall
      else
        testResults.overall
    Tests.Output(overall,
      testResults.events ++ multiNodeResults.events,
      testResults.summaries ++ multiNodeResults.summaries)
}

javaOptions in Test ++= Seq(
  "-Dconfig.file=src/test/resources/application.conf",
  "-Dlogback.configurationFile=src/test/resources/logback-test.xml")
