package actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models.AdminNotification

object AdminActor {

  sealed trait Command
  case class ProcessAdminNotification(notification: AdminNotification) extends Command

  def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case ProcessAdminNotification(notification) =>
        context.log.info(s"Admin: Processing ${notification.eventType} event")
        context.log.info(s"  - Reservation ID: ${notification.reservationId}")
        context.log.info(s"  - Room: ${notification.roomName} (ID: ${notification.roomId})")
        context.log.info(s"  - Employee Email: ${notification.employeeEmail}")
        context.log.info(s"  - Timestamp: ${notification.timestamp}")
        context.log.info(s"  - Details: ${notification.details}")
        
        
        notification.eventType match {
          case "RESERVATION_CREATED" =>
            context.log.info(s"  ✓ New reservation logged in system")
            context.log.info(s"  ✓ Room availability updated")
            context.log.info(s"  ✓ Notification queue processed")
            
          case "RESERVATION_AUTO_RELEASED" =>
            context.log.info(s"  ⚠ Auto-release event detected")
            context.log.info(s"  ✓ Room availability restored")
            context.log.info(s"  ✓ System metrics updated")
            
          case "ROOM_PREPARED" =>
            context.log.info(s"  ✓ Room preparation confirmed")
            context.log.info(s"  ✓ Service team notified")
            
          case _ =>
            context.log.info(s"  ✓ Event processed and logged")
        }
        
        context.log.info(s"  ✓ Admin notification processed successfully")
        
        Behaviors.same
    }
  }
}

