package com.pjanof.io

import PubSub._

import akka.actor.{ Actor, ActorSystem, Props }
import akka.io.Tcp
import akka.testkit.{ TestActorRef, TestKit, ImplicitSender }
import akka.util.ByteString

import scala.concurrent.duration._

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

class PubSubSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("PubSubSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  class Parent extends Actor {
    context.actorOf(Props[Server], "server")
    def receive = {
      case msg => testActor forward msg
    }
  }

  "connect multiple clients with send with close" in {

    val server = system.actorOf(Props(classOf[Parent], this), "parent")
    val listen = expectMsgType[Tcp.Bound].localAddress

    val client1 = system.actorOf(Client.props(listen, testActor), "client1")

    watch(client1)

    val c1, c2 = expectMsgType[Tcp.Connected]
    c1.localAddress should be(c2.remoteAddress)
    c2.localAddress should be(c1.remoteAddress)

    val client2 = system.actorOf(Client.props(listen, testActor), "client2")

    watch(client2)

    val c3, c4 = expectMsgType[Tcp.Connected]
    c3.localAddress should be(c4.remoteAddress)
    c4.localAddress should be(c3.remoteAddress)

    client1 ! ByteString("foo")
    expectMsgType[ByteString].utf8String should be("foo")

    client2 ! ByteString("bar")
    expectMsgType[ByteString].utf8String should be("bar")
    expectMsgType[ByteString].utf8String should be("bar")

    client1 ! "close"
    expectMsg("connection closed")
    expectTerminated(client1, 1.second)

    client2 ! "close"
    expectMsg("connection closed")
    expectTerminated(client2, 1.second)
  }
}
