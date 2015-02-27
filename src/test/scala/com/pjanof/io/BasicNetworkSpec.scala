package com.pjanof.io

import BasicNetwork._

import akka.actor.{ Actor, ActorSystem, Props }
import akka.io.Tcp
import akka.testkit.{ TestActorRef, TestKit, ImplicitSender }
import akka.util.ByteString

import scala.concurrent.duration._

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

class BasicNetworkSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("BasicNetworkSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  class Parent extends Actor {
    context.actorOf(Props[Server], "server")
    def receive = {
      case msg => testActor forward msg
    }
  }

  "connect with send with close" in {

    val server = system.actorOf(Props(classOf[Parent], this), "parent")
    val listen = expectMsgType[Tcp.Bound].localAddress
    val client = system.actorOf(Props(classOf[Client], listen, testActor), "client")

    watch(client)

    val c1, c2 = expectMsgType[Tcp.Connected]
    c1.localAddress should be(c2.remoteAddress)
    c2.localAddress should be(c1.remoteAddress)

    client ! ByteString("hello")
    expectMsgType[ByteString].utf8String should be("hello")

    client ! "close"
    expectMsg("connection closed")
    expectTerminated(client, 1.second)
  }
}
