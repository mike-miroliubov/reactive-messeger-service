package actor

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import model.{IncomingMessage, Message}
import play.api.libs.json.{JsResult, JsValue, Reads}

import play.api.libs.json._ // JSON library
import play.api.libs.json.Reads._ // Custom validation helpers
import play.api.libs.functional.syntax._ // Combinator syntax

/**
  * Created by Mikhail_Miroliubov on 6/2/2017.
  */
class UserActor(out: ActorRef, chatRoom: ActorRef, persistActor: ActorRef, name: String) extends Actor {
  val log = Logging(context.system, this)
  chatRoom ! Join(name)
  log.info("ACTOR PATH:" + self.path)

  override def receive: Receive = {
    case msg: JsValue =>
      val res: JsResult[IncomingMessage] = msg.validate(UserActor.messageReads)
      val message = res.get

      if (message.to != null) {
        context.actorSelection("akka://application/user/" + message.to) ! Message(name, message.text, Option(message.to))
        log.info("Transfer message to " + message.to)
      } else {
        chatRoom ! Message(name, message.text, Option.empty)
        log.info("Transfer message to chatroom")
      }

      persistActor ! Message(name, message.text, Option(message.to))
    case msg: Message => out ! msg
  }

  override def postStop() = chatRoom ! Leave(name)

}

object UserActor {
  def props(out: ActorRef, chatRoom: ActorRef, persistActor: ActorRef, name: String) =
    Props(new UserActor(out, chatRoom, persistActor, name))

  implicit val messageReads: Reads[IncomingMessage] = (
    (JsPath \ "from").read[String] and
      (JsPath \ "text").read[String] and
      (JsPath \ "to").readNullable[String]
    )((a, b, c) => IncomingMessage.apply(a, b, if (c.isDefined) c.get else null))
}
