package com.pjanof.io

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.io.Tcp._
import akka.util.ByteString

import java.net.InetSocketAddress

object ReadBackPressure {

  class Listener(monitor: ActorRef) extends Actor with ActorLogging {

    import context.system

    override def preStart: Unit =
      IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 0), pullMode = true)

    def receive = {
      case Bound(localAddress) =>
        log.info(s"Listener Bound: [ $localAddress ]")
        // Accept connections one by one
        sender() ! ResumeAccepting(batchSize = 1)
        context.become(listening(sender()))
        monitor ! localAddress
    }

    def listening(listener: ActorRef): Receive = {
      case Connected(remote, local) =>
        log.info(s"Listener Connected: [ $remote ] with [ $local ]")
        val handler = context.actorOf(Props(classOf[PullEcho], sender()))
        sender() ! Register(handler, keepOpenOnPeerClosed = true)
        listener ! ResumeAccepting(batchSize = 1)
    }
  }

  case object Ack extends Event

  class PullEcho(connection: ActorRef) extends Actor with ActorLogging {

    override def preStart: Unit = connection ! ResumeReading

    def receive = {
      case Received(data) =>
        log.info(s"Pull Echo Received: [ $data ]")
        connection ! Write(data, Ack)
      case Ack            =>
        log.info("Pull Echo Ack")
        connection ! ResumeReading
    }
  }
}
