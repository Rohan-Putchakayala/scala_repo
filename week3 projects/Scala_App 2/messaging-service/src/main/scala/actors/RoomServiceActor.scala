package actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import models.{RoomPreparationNotification, RoomReleaseNotification}

object RoomServiceActor {

  sealed trait Command
  case class ProcessRoomPreparation(notification: RoomPreparationNotification) extends Command
  case class ProcessRoomRelease(notification: RoomReleaseNotification) extends Command

  def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case ProcessRoomPreparation(notification) =>
        context.log.info(s"Room Service: Processing room preparation for reservation ${notification.reservationId}")
        context.log.info(s"  - Room: ${notification.roomName} (${notification.location})")
        context.log.info(s"  - Employee: ${notification.employeeName} (${notification.employeeEmail})")
        context.log.info(s"  - Department: ${notification.department}")
        context.log.info(s"  - Purpose: ${notification.purpose}")
        context.log.info(s"  - Time: ${notification.startTime} to ${notification.endTime}")
        
        
        context.log.info(s"  ✓ Room cleaning scheduled")
        context.log.info(s"  ✓ Equipment setup initiated")
        context.log.info(s"  ✓ Room access verified")
        context.log.info(s"  ✓ Room preparation checklist completed")
        context.log.info(s"  ✓ Room ready for ${notification.employeeName}")
        
        Behaviors.same

      case ProcessRoomRelease(notification) =>
        context.log.info(s"Room Service: Processing room release for reservation ${notification.reservationId}")
        context.log.info(s"  - Room: ${notification.roomName}")
        context.log.info(s"  - Employee: ${notification.employeeName}")
        context.log.info(s"  - Scheduled Start: ${notification.scheduledStartTime}")
        context.log.info(s"  - Release Time: ${notification.releaseTime}")
        
        
        context.log.info(s"  ✓ Room released from reservation")
        context.log.info(s"  ✓ Room cleaning initiated")
        context.log.info(s"  ✓ Equipment reset in progress")
        context.log.info(s"  ✓ Room available for next reservation")
        
        Behaviors.same
    }
  }
}

