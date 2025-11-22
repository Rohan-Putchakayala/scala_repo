package actors

import akka.actor.{Actor, ActorLogging, Props}
import models.ITSupportNotification

class ITSupportActor extends Actor with ActorLogging {
  import ITSupportActor._

  override def receive: Receive = {
    case notification: ITSupportNotification =>
      log.info(s"Processing IT notification for visitor ${notification.visitorId}")
      log.info(s"Visitor Name: ${notification.visitorName}")
      log.info(s"Host Email: ${notification.hostEmail}")
      log.info(s"Check-in Time: ${notification.checkInTime}")
  }
}

object ITSupportActor {
  def props(): Props = Props(new ITSupportActor)
  case class ProcessNotification(notification: ITSupportNotification)
}