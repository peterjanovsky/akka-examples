package com.pjanof.io.actors                                                    

import akka.actor.{ Actor, ActorLogging, ActorPath, ActorRef, Props }                         

import com.typesafe.scalalogging.StrictLogging

import java.util.UUID

trait Node { actor: Actor =>                                                    

  def send(actorRef: ActorRef, msg: Any) = { actorRef.tell(msg, self) }         

  def reply(msg: Any) = { sender ! msg }                                        

  def forward(actorRef: ActorRef, msg: Any) = { actorRef.forward(msg) }         

  def actorOf(props: Props): ActorRef = actor.context.actorOf(props)            
}

trait TrackingActor extends Actor with ActorLogging {

  val uuid: UUID = UUID.randomUUID

  def wrappedReceive: Receive

  def receive = {
    case msg: Any =>
      log.info(s"Tracking Actor [ $uuid ]: $msg")
      wrappedReceive(msg)
  }
}

trait InstrumentedActor extends TrackingActor {

  override def receive = {
    case msg: Any =>
      log.info(s"Instrumented Actor [ $uuid ]: $msg")
      super.receive(msg)
  }
}
