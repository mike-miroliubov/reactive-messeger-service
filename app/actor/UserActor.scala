package actor

import java.util.Date

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import manager.MessageManager
import model.{IncomingMessage, Message}
import play.api.libs.json.{JsResult, JsValue, Reads}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._ // Combinator syntax

/**
  * Created by Mikhail_Miroliubov on 6/2/2017.
  */
class UserActor(out: ActorRef, chatRoom: ActorRef, name: String, manager: MessageManager) extends Actor {
  val log = Logging(context.system, this)
  chatRoom ! Join(name)
  log.info("ACTOR PATH:" + self.path)

  override def receive: Receive = {
    case msg: JsValue =>
      val res: JsResult[IncomingMessage] = msg.validate(UserActor.messageReads)
      val incomingMessage = res.get
      val message = Message(name, incomingMessage.text,
        if (incomingMessage.to != null) Option(incomingMessage.to) else Option.empty, new Date(), false, false)

      if (incomingMessage.to != null) {
        context.actorSelection("akka://application/user/" + incomingMessage.to) ! message
        log.info("Transfer message to " + incomingMessage.to)
      } else {
        chatRoom ! message
        log.info("Transfer message to chatroom")
      }

      manager.saveMessage(message)
    case msg: Message => out ! msg
  }

  override def postStop() = chatRoom ! Leave(name)

}

object UserActor {
  def props(out: ActorRef, chatRoom: ActorRef, name: String, manager: MessageManager) =
    Props(new UserActor(out, chatRoom, name, manager))

  implicit val messageReads: Reads[IncomingMessage] = (
    (JsPath \ "from").read[String] and
      (JsPath \ "text").read[String] and
      (JsPath \ "to").readNullable[String]
    )((a, b, c) => IncomingMessage.apply(a, b, if (c.isDefined) c.get else null))
}
