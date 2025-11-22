package actors

import akka.actor.{Actor, ActorLogging, Props}
import models.SecurityNotification

class SecurityActor extends Actor with ActorLogging {
  import SecurityActor._

  override def receive: Receive = {
    case notification: SecurityNotification =>
      log.info(s"Processing security notification for visitor ${notification.visitorId}")
      log.info(s"Visitor Name: ${notification.visitorName}")
      log.info(s"ID Proof: ${notification.idProofNumber}")
      log.info(s"Check-in Time: ${notification.checkInTime}")
  }
}

object SecurityActor {
  def props(): Props = Props(new SecurityActor)
  case class ProcessNotification(notification: SecurityNotification)
}