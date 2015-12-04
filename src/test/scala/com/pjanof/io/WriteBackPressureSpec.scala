package com.pjanof.io

import WriteBackPressure._

import akka.actor.{ Actor, ActorSystem, Props }
import akka.io.Tcp
import akka.testkit.{ TestActorRef, TestKit, ImplicitSender }
import akka.util.ByteString

import scala.concurrent.duration._

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

class WriteBackPressureSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("WriteBackPressureSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  class Parent extends Actor {
    context.actorOf(Props(classOf[HandlerManager], classOf[AckHandler]), "ack-handler")
    def receive = {
      case msg => testActor forward msg
    }
  }

  "connect with send with ack with close" in {

    val manager = system.actorOf(Props(classOf[Parent], this), "parent")
    val listen = expectMsgType[Tcp.Bound].localAddress

    val client = system.actorOf(Client.props(listen, testActor), "client")

    watch(client)

    val c1, c2 = expectMsgType[Tcp.Connected]
    c1.localAddress should be(c2.remoteAddress)
    c2.localAddress should be(c1.remoteAddress)

/*
    client ! ByteString("foo")
    expectMsgType[ByteString].utf8String should be ("foo")

    client ! ByteString("bar")
    expectMsgType[ByteString].utf8String should be ("bar")
*/
    client ! ByteString("foo")
    client ! ByteString("bar")
    client ! ByteString("baz")
    expectMsgType[ByteString].utf8String should be ("foobar")
    expectMsgType[ByteString].utf8String should be ("baz")

    client ! "close"
    expectMsg("connection closed")
    expectTerminated(client, 1.second)
  }
}
