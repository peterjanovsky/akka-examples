package com.pjanof.io

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString

import java.net.InetSocketAddress

object BasicNetwork {

  object Client {
    def props(remote: InetSocketAddress, replies: ActorRef) =
      Props(classOf[Client], remote, replies)
  }

  class Client(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {

    import Tcp._
    import context.system

    /**
      * The manager is an actor which
      *   - handles underlying low level I/O ( selectors, channels )
      *   - instantiates workers for specific tasks ( ex: listening to incoming connections )
      */
    IO(Tcp) ! Connect(remote)

    def receive = {
      case CommandFailed(_: Connect) =>
        log.info("Client Command Failed")
        listener ! "connect failed"
        context stop self

      case c @ Connected(remote, local) =>
        log.info(s"Client Connected: [ $remote ] with [ $local ]")
        listener ! c
        val connection = sender()
        connection ! Register(self)
        context become {
          case data: ByteString =>
            log.info("Client ByteString Received / Written")
            connection ! Write(data)

          case CommandFailed(w: Write) =>
            // O/S buffer was full
            log.error("Client OS Buffer Full")
            listener ! "write failed"

          case Received(data) =>
            log.info(s"Client Received: [ $data ]")
            listener ! data

          case "close" =>
            log.info("Client Close")
            connection ! Close

          case _: ConnectionClosed =>
            log.info("Client Connected Closed")
            listener ! "connection closed"
            context stop self
        }

      case _ =>
        log.info("Client Received: [ Case Not Handled ]")
    }
  }

  class Server extends Actor with ActorLogging {

    import Tcp._
    import context.system

    IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 0))

    def receive = {
      case b @ Bound(localAddress) =>
        log.info(s"Server Bound: [ $b ] at [ $localAddress ]")
        context.parent ! b

      case CommandFailed(_: Bind) =>
        log.info("Server Command Failed")
        context stop self

      case c @ Connected(remote, local) =>
        log.info(s"Server Connected: [ $remote ] with [ $local ]")
        context.parent ! c
        val handler = context.actorOf(Props[ConnectionHandler])
        val connection = sender()
        connection ! Register(handler)

      case _ =>
        log.info("Server Received: [ Case Not Handled ]")
    }
  }

  class ConnectionHandler extends Actor with ActorLogging {

    import Tcp._

    def receive = {
      case Received(data) =>
        log.info(s"Handler Received: [ $data ]")
        sender() ! Write(data)

      case PeerClosed     =>
        log.info("Handler Peer Closed")
        context stop self

      case _ =>
        log.info("Handler Received: [ Case Not Handled ]")
    }
  }
}
