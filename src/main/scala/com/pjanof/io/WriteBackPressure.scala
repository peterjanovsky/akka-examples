package com.pjanof.io

import akka.actor.{ Actor, ActorLogging, ActorRef, Props, SupervisorStrategy }
import akka.io.{ IO, Tcp }
import akka.util.ByteString

import com.typesafe.config.{ Config, ConfigFactory }

import java.net.InetSocketAddress

/**
  * background
  *   TCP connection actor does not support internal buffering
  *     handles single write while it is being passed on to the O/S kernel
  *     congestion must be handled at the user level for reads and writes
  *
  * modes of back-pressuring writes
  *   ACK-based
  *     Write command carries an arbitrary object
  *       if object is not Tcp.NoAck it will be returned to the sender of the Write
  *         happens after successfully writing all contained data to the socket
  *       write initiated before receiving acknowledgement results in buffer overrun
  *   NACK-based
  *     writes arriving while previous write has not been completed are replied to with
  *       CommandFailed message containing the failed write
  *     this mechanism requires the implemented protocol to tolerate skipping writes
  *     enabled by setting the useResumeWriting flag to false
  *       within the Register message during connection activation
  *   NACK-based with write suspending
  *     similiar to NACK-based writing
  *     after a write fails no further writes will succeed until
  *       ResumeWriting message is received
  *     ResumeWriting is answered with WritingResumed message after
  *       last accepted write has completed
  *     if actor handling the connection implements buffering
  *       it must resend NACK'ed messages after receiving the WritingResumed signal
  *       ensures every message is delivered exactly once to network socket
  *
  * modes of back-pressuring reads
  *   Push-reading
  *     connection actor sends the registered reader actor incoming data
  *       as it is available via Received events
  *     if the reader actor wants to signal back-pressure to remote TCP endpoint
  *       it may send a SuspendReading message to connetion actor
  *         indicates reader actor wants to suspend receiving new data
  *     Received events will not arrive until a corresponding ResumeReading is sent
  *       indicates the reader actor is ready again
  *   Pull-reading
  *     after sending a Received event the connection actor automatically
  *       suspends accepting data from the socket until reader actor signals via
  *         ResumeReading message it is ready to process more input data
  *     new data is "pulled" from the connection by sending ResumeReading
  *
  * these schemes only work between
  *   one writer/reader and one connection actor
  * consistent results can not be achieved with
  *   multiple actors sending write commands to a single connection
  */
object WriteBackPressure {

  object Client {
    def props(remote: InetSocketAddress, replies: ActorRef) =
      Props(classOf[Client], remote, replies)
  }

  class Client(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {

    import Tcp._
    import context.system

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

  class HandlerManager(handlerClass: Class[_]) extends Actor with ActorLogging {

    import Tcp._
    import context.system

    // do not recover when connection is broken
    override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

    /**
      * bind to the listen port
      * port will automatically be closed once this actor dies
      */
    override def preStart(): Unit = {
      IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 0))
    }

    // do not restart
    override def postRestart(thr: Throwable): Unit = context stop self

    def receive = {

      case b @ Bound(localAddress) =>
        log.info(s"Server Bound: [ $b ] at [ $localAddress ]")
        context.parent ! b

      case CommandFailed(Bind(_, local, _, _, _)) =>
        log.warning(s"Server Command Failed, Cannot Bind to [$local]")
        context stop self

      case c @ Connected(remote, local) =>
        log.info(s"Server Connected: [ $remote ] with [ $local ]") 
        context.parent ! c
        val handler = context.actorOf(Props(handlerClass, sender(), remote))
        sender() ! Register(handler, keepOpenOnPeerClosed = true)

        log.info(s"Handler: [ $handler ]")  
        log.info(s"Sender: [ $sender ]")
    }
  }

  /**
    * connection must remain half-open when the remote side has closed its writing end
    *   allows handler to write outstanding data to the client before closing the connection
    *   enabled using a flag during connection activation
    * after chunk is written wait for the Ack before sending the next chunk
    * while waiting
    *   switch behavior to buffer incoming data
    */
  class AckHandler(connection: ActorRef, remote: InetSocketAddress) extends Actor with ActorLogging {

    import Tcp._

    // sign death pact, actor terminates when connection breaks
    context watch connection

    case object Ack extends Event

    def receive = {

      case Received(data) =>
        log.info(s"Handler Received: [ $data ]") 
        buffer(data)
        connection ! Write(data, Ack)

        context.become({
          case Received(data) =>
            log.info(s"Handler within Become Received: [ $data ]") 
            buffer(data)

          case Ack =>
            log.info("Handler within Become ACK")
            acknowledge

          case PeerClosed =>
            log.info("Handler within Become Peer Closed")
            closing = true

          case unhandled =>
            log.info(s"Handler within Become Received: [ Case Not Handled - $unhandled ]")

        }, discardOld = false)

      case PeerClosed =>
        log.info("Handler Peer Closed")
        context stop self

      case unhandled =>
        log.info(s"Handler Received: [ Case Not Handled - $unhandled ]")
    }

    var storage = Vector.empty[ByteString]
    var stored = 0L
    var transferred = 0L
    var closing = false

    val maxStored = 100000000L
    val highWatermark = maxStored * 5 / 10
    val lowWatermark = maxStored * 3 / 10
    var suspended = false

    private def buffer(data: ByteString): Unit = {
      storage :+= data
      stored += data.size

      if (stored > maxStored) {

        log.warning(s"drop connection to [$remote] (buffer overrun)")
        context stop self

      } else if (stored > highWatermark) {

        log.debug(s"suspending reading")
        connection ! SuspendReading
        suspended = true
      }
    }

    private def acknowledge(): Unit = {
      require(storage.nonEmpty, "storage was empty")

      val size = storage(0).size
      stored -= size
      transferred += size

      storage = storage drop 1

      if (suspended && stored < lowWatermark) {
        log.debug("resuming reading")
        connection ! ResumeReading
        suspended = false
      }

      if (storage.isEmpty) {
        if (closing) context stop self
        else context.unbecome()
      } else connection ! Write(storage(0), Ack)
    }
  }
}
