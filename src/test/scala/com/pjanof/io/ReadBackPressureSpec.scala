package com.pjanof.io

import ReadBackPressure._

import akka.actor.{ Actor, ActorSystem, Props }
import akka.io.{ IO, Tcp }
import akka.io.Tcp._
import akka.testkit.{ TestActorRef, TestKit, TestProbe, ImplicitSender }
import akka.util.ByteString

import java.net.InetSocketAddress

import scala.concurrent.Await
import scala.concurrent.duration._

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

class PullReadingSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("PullReadingSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "read back-pressure with pull mode" in {

    val probe = TestProbe()
    system.actorOf(Props(classOf[Listener], probe.ref), "server")
    val listenAddress = probe.expectMsgType[InetSocketAddress]

    IO(Tcp) ! Connect(listenAddress, pullMode = true)
    expectMsgType[Connected]
    val connection = lastSender

    val client = TestProbe()
    client.send(connection, Register(client.ref))
    client.send(connection, Write(ByteString("hello")))
    client.send(connection, ResumeReading)
    client.expectMsg(Received(ByteString("hello")))

    Await.ready(system.terminate(), Duration.Inf)
  }
}
