package com.pjanof.clustering

import com.typesafe.config.ConfigFactory

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import akka.actor.{ Props, Actor }
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.remote.testkit.MultiNodeSpecCallbacks
import akka.testkit.ImplicitSender

import scala.concurrent.duration._

trait SimpleClusterSpec extends MultiNodeSpecCallbacks
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def beforeAll = multiNodeSpecBeforeAll

  override def afterAll = multiNodeSpecAfterAll
}

object SimpleClusterConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")

  def nodeList = Seq(node1, node2)

  nodeList foreach { role =>
    nodeConfig(role) {
      ConfigFactory.parseString(s"""
        akka.cluster.metrics.enabled=off
        akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
        akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
      """)
    }
  }

  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.remote.log-remote-lifecycle-events = off
    akka.cluster.roles = [compute]
  """))
}

class SimpleClusterMultiJvmNode1 extends SimpleCluster
class SimpleClusterMultiJvmNode2 extends SimpleCluster

object SimpleCluster {
  class Pong extends Actor {
    def receive = {
      case "ping" => sender ! "pong"
    }
  }
}

/** distributing the jar files and kicking off the tests remotely
  *   multi-node-test
  *
  * run all JVMs on the local machine
  *   multi-jvm:test
  *
  * run individual tests
  *   multi-node-test-only your.test.name
  *
  * run individual tests in the multi-jvm mode
  *   multi-jvm:test-only your.test.name
  */
class SimpleCluster extends MultiNodeSpec(SimpleClusterConfig)
  with SimpleClusterSpec with ImplicitSender {

  import SimpleClusterConfig._
  import SimpleCluster._

  override def initialParticipants = roles.size

  "Join cluster" in within(15.seconds) {

    Cluster(system).subscribe(testActor, classOf[MemberUp])
    expectMsgClass(classOf[CurrentClusterState])

    val node1Address = node(node1).address
    val node2Address = node(node2).address

    Cluster(system) join node1Address

    system.actorOf(Props[SimpleClusterActor], "simpleClusterActor")

    receiveN(2).collect { case MemberUp(m) => m.address }.toSet should be(
      Set(node1Address, node2Address))

    Cluster(system).unsubscribe(testActor)

    testConductor.enter("all-up")
  }

  "send ping" in within(15 seconds) {

    runOn(node2) {

      log.info(s"Node-1 Path: ${node(node1)}")
      log.info(s"Node-2 Path: ${node(node2)}")

      val ref = system.actorSelection(node(node2) / "user" / "simpleClusterActor")
      awaitAssert {
        ref ! "ping"
        expectMsgType[String](1.second) should be ("pong")
      }
    }

    testConductor.enter("done-2")
  }

  "send ding" in within(15 seconds) {

    runOn(node1) {

      log.info(s"Node-1 Path: ${node(node1)}")
      log.info(s"Node-2 Path: ${node(node2)}")

      val ref = system.actorSelection(node(node1) / "user" / "simpleClusterActor")
      awaitAssert {
        ref ! "ding"
        expectMsgType[String](1.second) should be ("dong")
      }
    }

    testConductor.enter("done-3")
  }
}
