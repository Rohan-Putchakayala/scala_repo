package actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import models._
import services.{EmailService, KafkaProducerService}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import java.time.{Instant, LocalDateTime}

object ReservationActor {

  
  sealed trait Command
  case class SendReservationNotifications(
    reservation: Reservation,
    room: Room,
    replyTo: ActorRef[ReservationNotificationResult]
  ) extends Command
  case class SendReminderNotification(
    reservation: Reservation,
    room: Room,
    replyTo: ActorRef[ReservationNotificationResult]
  ) extends Command
  case class SendReleaseNotifications(
    reservation: Reservation,
    room: Room,
    replyTo: ActorRef[ReservationNotificationResult]
  ) extends Command

  
  private sealed trait InternalMsg extends Command
  private case class ReservationNotificationsCompleted(replyTo: ActorRef[ReservationNotificationResult]) extends InternalMsg
  private case class ReservationNotificationsFailed(reason: String, replyTo: ActorRef[ReservationNotificationResult]) extends InternalMsg

  
  sealed trait ReservationNotificationResult
  case class ReservationNotificationSuccess(message: String) extends ReservationNotificationResult
  case class ReservationNotificationFailure(error: String) extends ReservationNotificationResult

  def apply(emailService: EmailService, kafkaProducer: KafkaProducerService)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        
        case SendReservationNotifications(reservation, room, replyTo) =>
          replyTo ! ReservationNotificationSuccess("Reservation notifications started")

          
          context.pipeToSelf(emailService.sendBookingConfirmation(
            reservation.employeeEmail,
            reservation.employeeName,
            room.name,
            room.location,
            reservation.startTime.toString,
            reservation.endTime.toString,
            reservation.purpose
          )) {
            case Success(_) => ReservationNotificationsCompleted(replyTo)
            case Failure(ex) => ReservationNotificationsFailed(ex.getMessage, replyTo)
          }

          
          context.pipeToSelf(kafkaProducer.sendRoomPreparationNotification(
            "room-preparation-notifications",
            RoomPreparationNotification(
              reservationId = reservation.id.get,
              roomId = room.id.get,
              roomName = room.name,
              location = room.location,
              employeeName = reservation.employeeName,
              employeeEmail = reservation.employeeEmail,
              department = reservation.department,
              purpose = reservation.purpose,
              startTime = reservation.startTime,
              endTime = reservation.endTime,
              reservationTime = reservation.createdAt
            )
          )) {
            case Success(_) => ReservationNotificationsCompleted(replyTo)
            case Failure(ex) => ReservationNotificationsFailed(ex.getMessage, replyTo)
          }

          
          context.pipeToSelf(kafkaProducer.sendAdminNotification(
            "admin-notifications",
            AdminNotification(
              eventType = "RESERVATION_CREATED",
              reservationId = reservation.id.get,
              roomId = room.id.get,
              roomName = room.name,
              employeeEmail = reservation.employeeEmail,
              timestamp = Instant.now(),
              details = s"New reservation created for ${reservation.employeeName} in ${room.name}"
            )
          )) {
            case Success(_) => ReservationNotificationsCompleted(replyTo)
            case Failure(ex) => ReservationNotificationsFailed(ex.getMessage, replyTo)
          }

          Behaviors.same

        case ReservationNotificationsCompleted(replyTo) =>
          
          Behaviors.same

        case ReservationNotificationsFailed(reason, replyTo) =>
          
          Behaviors.same

        
        case SendReminderNotification(reservation, room, replyTo) =>
          replyTo ! ReservationNotificationSuccess("Reminder notification started")
          context.pipeToSelf(emailService.sendReservationReminder(
            reservation.employeeEmail,
            reservation.employeeName,
            room.name,
            room.location,
            reservation.startTime.toString
          )) {
            case Success(_) => ReservationNotificationsCompleted(replyTo)
            case Failure(ex) => ReservationNotificationsFailed(ex.getMessage, replyTo)
          }
          Behaviors.same

        
        case SendReleaseNotifications(reservation, room, replyTo) =>
          replyTo ! ReservationNotificationSuccess("Release notifications started")

          
          context.pipeToSelf(emailService.sendAutoReleaseNotification(
            reservation.employeeEmail,
            reservation.employeeName,
            room.name,
            reservation.startTime.toString
          )) {
            case Success(_) => ReservationNotificationsCompleted(replyTo)
            case Failure(ex) => ReservationNotificationsFailed(ex.getMessage, replyTo)
          }

          
          context.pipeToSelf(kafkaProducer.sendRoomReleaseNotification(
            "room-release-notifications",
            RoomReleaseNotification(
              reservationId = reservation.id.get,
              roomId = room.id.get,
              roomName = room.name,
              employeeName = reservation.employeeName,
              employeeEmail = reservation.employeeEmail,
              scheduledStartTime = reservation.startTime,
              releaseTime = Instant.now()
            )
          )) {
            case Success(_) => ReservationNotificationsCompleted(replyTo)
            case Failure(ex) => ReservationNotificationsFailed(ex.getMessage, replyTo)
          }

          
          context.pipeToSelf(kafkaProducer.sendAdminNotification(
            "admin-notifications",
            AdminNotification(
              eventType = "RESERVATION_AUTO_RELEASED",
              reservationId = reservation.id.get,
              roomId = room.id.get,
              roomName = room.name,
              employeeEmail = reservation.employeeEmail,
              timestamp = Instant.now(),
              details = s"Reservation auto-released for ${reservation.employeeName} in ${room.name}"
            )
          )) {
            case Success(_) => ReservationNotificationsCompleted(replyTo)
            case Failure(ex) => ReservationNotificationsFailed(ex.getMessage, replyTo)
          }

          Behaviors.same
      }
    }
  }
}

