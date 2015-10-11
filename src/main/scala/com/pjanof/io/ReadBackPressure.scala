package com.pjanof.io

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.io.Tcp._
import akka.util.ByteString

import java.net.InetSocketAddress

/**
  * background
  *  with push based reading
  *    data coming from the socket is sent to the actor when it's available
  *    requires maintaining a buffer of incoming data as the rate of writing may be slower than the data arrival rate
  *
  *  with pull mode
  *    this buffer is eliminated
  *    reading resumes after the previous write has been completely acknowledged by the connection actor
  *    connection actors start from suspended state
  *    to start the flow of data
  *      send ResumeReading in the preStart method
  *      informs the connection actor we are ready to receive the first chunk of data
  *    no need for maintaining a buffer
  *      as reading resumes only when the previous data chunk has been completely written
  */
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
