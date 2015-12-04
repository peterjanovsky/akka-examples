package com.pjanof.io.actors

import akka.actor.{ Actor, ActorRef, Props }

import java.util.UUID

import scala.collection.mutable.{ Set => MutableSet }

case class Spider(home: ActorRef, trail: ActorNodeTrail = ActorNodeTrail())

case class ActorNodeTrail(collected: Set[ActorRef] = Set(), uuid: UUID = UUID.randomUUID())

case class ActorNodeRef(node: ActorRef, in: List[ActorRef], out: List[ActorRef])

trait ActorNode[Data, Request] extends Actor with Node {

  // pathways coming into the node
  protected val in = MutableSet[ActorRef]()

  // pathways going out of the node
  protected val out = MutableSet[ActorRef]()

  // used to only handle a request traveling through system
  protected var lastId: Option[UUID] = None

  def collect(req: Request): Option[Data]

  def selfNode = ActorNodeRef(self, in.toList, out.toList)

  override def send(actorRef: ActorRef, msg: Any) {
    recordOutput(actorRef)
    actorRef tell (msg, self)
  }

  override def forward(actorRef: ActorRef, msg: Any) {
    recordOutput(actorRef)
    actorRef forward msg
  }

  override def actorOf(props: Props): ActorRef = {
    val actorRef = context.actorOf(props)
    recordOutput(actorRef)
    actorRef
  }

  override def reply(msg: Any) {
    recordOutput(sender)
    sender ! msg
  }

  def recordOutput(actorRef: ActorRef) {
    out.add(actorRef)
  }

  def recordInput(actorRef: ActorRef) {
    if (actorRef != context.system.deadLetters){
      in.add(actorRef)
    }
  }

  def wrappedReceive: Receive = {
    case msg: Any if ! msg.isInstanceOf[(Request, Spider)] =>
      recordInput(sender)
      before(msg)
      super.receive(msg)
      after(msg)
  }

  abstract override def receive = handleRequest orElse wrappedReceive

  def before: Receive

  def after: Receive

  def sendSpiders(ref: ActorRef, data: Data, msg: (Request, Spider), collected: Set[ActorRef]) {
    val (request, spider) = msg
    val newTrail = spider.trail.copy(collected = collected + self)
    val newSpider = spider.copy(trail = newTrail)
    in.filterNot(in => collected.contains(in)).foreach(_ ! (request,newSpider))
    out.filterNot(out => collected.contains(out)) foreach (_ ! (request,newSpider))
  }

  def handleRequest:Receive = {
    case (req: Request, spider @ Spider(ref, ActorNodeTrail(collected, uuid))) if !lastId.exists(_ == uuid) =>
      lastId = Some(uuid)
      collect(req).map { data =>
        sendSpiders(ref, data, (req,spider), collected)
      }
  }
}
