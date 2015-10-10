package com.pjanof.io

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString

import java.net.InetSocketAddress

object PubSub {

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

          case unhandled =>
            log.info(s"Client Become Received: [ Case Not Handled - $unhandled ]")
        }

      case unhandled =>
        log.info(s"Client Received: [ Case Not Handled - $unhandled ]")
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
        val handler = context.actorOf(SimplisticHandler.props(Set.empty[ActorRef]))
        val connection = sender()
        connection ! Register(handler)

        log.info(s"Handler: [ $handler ]")
        log.info(s"Sender: [ $sender ]")
        log.info(s"TCP Connection: [ $connection ]")

        context.become(connected(Set(connection)))

      case unhandled =>
        log.info(s"Server Received: [ Case Not Handled - $unhandled ]")
    }

    def connected(connections: Set[ActorRef]): Receive = {

      case c @ Connected(remote, local) =>
        log.info(s"Server Connected: [ $remote ] with [ $local ]")
        context.parent ! c
        val handler = context.actorOf(SimplisticHandler.props(connections))
        val connection = sender()
        connection ! Register(handler)

        log.info(s"Handler: [ $handler ]")
        log.info(s"Sender: [ $sender ]")
        log.info(s"TCP Connections: [ ${connections + connection} ]")

        context.become(connected(connections + connection))

      case unhandled =>
        log.info(s"Server Received: [ Case Not Handled - $unhandled ]")
    }
  }

  object SimplisticHandler {
    def props(connections: Set[ActorRef]) =
      Props(classOf[SimplisticHandler], connections)
  }

  class SimplisticHandler(connections: Set[ActorRef]) extends Actor with ActorLogging {

    import Tcp._

    def receive = {
      case Received(data) =>
        log.info(s"Handler Received: [ $data ]")
        sender() ! Write(data)

        connections.map { connection => {
          log.info(s"Writing to TCP Connection: $connection")
          connection ! Write(data)
        } }

      case PeerClosed =>
        log.info("Handler Peer Closed")
        context stop self

      case unhandled =>
        log.info(s"Handler Received: [ Case Not Handled - $unhandled ]")
    }
  }
}
