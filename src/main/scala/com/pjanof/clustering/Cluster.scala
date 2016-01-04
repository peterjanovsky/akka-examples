package com.pjanof.clustering

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props }
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.pattern.gracefulStop

import com.typesafe.config.{ Config, ConfigFactory }

import java.util.concurrent.{ ExecutorService, Executors }

import scala.concurrent.ExecutionContext

/** ActorSystem utilizes the default dispatcher
  */
object DefaultCluster {

  private val system: ActorSystem = ActorSystem("default-cluster-system")

  private val ref: ActorRef =
    system.actorOf(Props[SimpleClusterListener], "cluster-listener")

  def shutdown = ref ! PoisonPill
}

/** ActorSystem utilizes the default dispatcher which has been configured
  */
object ConfiguredCluster {

  private val config: Config = ConfigFactory.load

  private val system: ActorSystem =
    ActorSystem("configured-cluster-system", config)

  val listener: ActorRef =
    system.actorOf(Props[SimpleClusterListener], "cluster-listener")
}

/** ActorSystem utilizes the specified ExecutionContext
  *
  * ExecutionContext used as the default executor for all dispatchers within ActorSystem
  */
object SuppliedContextCluster {

  private val processors: Int = Runtime.getRuntime.availableProcessors

  private val es: ExecutorService = Executors.newFixedThreadPool(processors)

  private val ec: ExecutionContext = ExecutionContext.fromExecutorService(es)

  private val system: ActorSystem = ActorSystem("supplied-cluster-system")

  val listener: ActorRef =
    system.actorOf(Props[SimpleClusterListener], "cluster-listener")
}

object SimpleClusterListener {

  case object Leave
}

class SimpleClusterListener extends Actor with ActorLogging {

  import SimpleClusterListener._

  val cluster = Cluster(context.system)

  override def preStart(): Unit =
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])

  override def postStop(): Unit = {
    log.info("Stopping Actor")
    cluster.unsubscribe(self) }

  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)

    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)

    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}",
        member.address, previousStatus)

    case _: MemberEvent => // ignore

    case Leave =>
      log.info("Leaving Cluster")

    case msg =>
      log.info(s"Received: $msg")
  }
}

class SimpleClusterActor extends Actor with ActorLogging {

  def receive = {

    case "ping" =>
      log.info(s"SimpleClusterActor Received Ping from: $sender")
      sender ! "pong"

    case "ding" =>
      log.info(s"SimpleClusterActor Received Ding from: $sender")
      sender ! "dong"
  }
}
