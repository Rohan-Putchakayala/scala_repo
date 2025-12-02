package actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import models._
import services.EmailService

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Akka Typed actor responsible for processing different types of notification messages
 * consumed from Kafka topics. Coordinates with email service to send notifications
 * and handles processing results with appropriate logging and error handling.
 */
object NotificationProcessorActor {

  sealed trait Command

  /**
   * Command to process a notification message with tracking information.
   *
   * @param messageId unique identifier for the message
   * @param notification the notification message to process
   * @param startTime processing start timestamp for metrics
   */
  case class ProcessNotification(
    messageId: String,
    notification: NotificationMessage,
    startTime: Long
  ) extends Command

  /**
   * Command to handle processing failures with retry logic.
   *
   * @param messageId unique identifier for the failed message
   * @param error error description
   * @param retryable whether the failure can be retried
   */
  case class ProcessingFailed(
    messageId: String,
    error: String,
    retryable: Boolean
  ) extends Command

  private sealed trait InternalCommand extends Command

  /**
   * Internal command for successful notification processing completion.
   *
   * @param messageId unique identifier for the processed message
   * @param processingTime total processing time in milliseconds
   */
  private case class NotificationProcessed(
    messageId: String,
    processingTime: Long
  ) extends InternalCommand

  /**
   * Internal command for notification processing failure handling.
   *
   * @param messageId unique identifier for the failed message
   * @param error error description
   * @param processingTime time spent before failure
   */
  private case class NotificationProcessingFailed(
    messageId: String,
    error: String,
    processingTime: Long
  ) extends InternalCommand

  /**
   * Creates the main behavior for the notification processor actor.
   *
   * @param emailService service for sending email notifications
   * @param ec execution context for asynchronous operations
   * @return Behavior defining the actor's message handling logic
   */
  def apply(emailService: EmailService)(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("NotificationProcessorActor started")

      Behaviors.receiveMessage {

        case ProcessNotification(messageId, notification, startTime) =>
          context.log.debug(s"Processing notification message: $messageId")

          notification match {
            case RoomPreparationMessage(roomPrep) =>
              processRoomPreparationNotification(messageId, roomPrep, startTime, context, emailService)

            case ReservationReminderMessage(reminder) =>
              processReservationReminderNotification(messageId, reminder, startTime, context, emailService)

            case RoomReleaseMessage(release) =>
              processRoomReleaseNotification(messageId, release, startTime, context, emailService)

            case AdminMessage(admin) =>
              processAdminNotification(messageId, admin, startTime, context)
          }

          Behaviors.same

        case ProcessingFailed(messageId, error, retryable) =>
          context.log.warn(s"Processing failed for message $messageId: $error (retryable: $retryable)")
          if (retryable) {
            context.log.info(s"Message $messageId will be retried by Kafka consumer")
          }
          Behaviors.same

        case NotificationProcessed(messageId, processingTime) =>
          context.log.info(s"Successfully processed notification $messageId in ${processingTime}ms")
          Behaviors.same

        case NotificationProcessingFailed(messageId, error, processingTime) =>
          context.log.error(s"Failed to process notification $messageId after ${processingTime}ms: $error")
          Behaviors.same
      }
    }
  }

  /**
   * Processes room preparation notifications by logging facility preparation requirements.
   *
   * @param messageId unique message identifier
   * @param notification room preparation notification details
   * @param startTime processing start timestamp
   * @param context actor context for logging and self-messaging
   * @param emailService email service for potential notifications
   */
  private def processRoomPreparationNotification(
    messageId: String,
    notification: RoomPreparationNotification,
    startTime: Long,
    context: akka.actor.typed.scaladsl.ActorContext[Command],
    emailService: EmailService
  )(implicit ec: ExecutionContext): Unit = {

    context.log.info(s"Room preparation required for reservation ${notification.reservationId}: " +
      s"${notification.roomName} at ${notification.location} for ${notification.employeeName}")

    val processingTime = System.currentTimeMillis() - startTime
    context.self ! NotificationProcessed(messageId, processingTime)
  }

  /**
   * Processes reservation reminder notifications by sending email reminders to employees.
   *
   * @param messageId unique message identifier
   * @param notification reservation reminder notification details
   * @param startTime processing start timestamp
   * @param context actor context for logging and self-messaging
   * @param emailService email service for sending reminder emails
   */
  private def processReservationReminderNotification(
    messageId: String,
    notification: ReservationReminderNotification,
    startTime: Long,
    context: akka.actor.typed.scaladsl.ActorContext[Command],
    emailService: EmailService
  )(implicit ec: ExecutionContext): Unit = {

    context.log.info(s"Sending reservation reminder for reservation ${notification.reservationId} " +
      s"to ${notification.employeeEmail}")

    context.pipeToSelf(emailService.sendReservationReminder(
      notification.employeeEmail,
      notification.employeeName,
      notification.roomName,
      notification.location,
      notification.startTime.toString
    )) {
      case Success(_) =>
        val processingTime = System.currentTimeMillis() - startTime
        NotificationProcessed(messageId, processingTime)
      case Failure(exception) =>
        val processingTime = System.currentTimeMillis() - startTime
        NotificationProcessingFailed(messageId, exception.getMessage, processingTime)
    }
  }

  /**
   * Processes room release notifications by sending auto-release emails to employees.
   *
   * @param messageId unique message identifier
   * @param notification room release notification details
   * @param startTime processing start timestamp
   * @param context actor context for logging and self-messaging
   * @param emailService email service for sending release notifications
   */
  private def processRoomReleaseNotification(
    messageId: String,
    notification: RoomReleaseNotification,
    startTime: Long,
    context: akka.actor.typed.scaladsl.ActorContext[Command],
    emailService: EmailService
  )(implicit ec: ExecutionContext): Unit = {

    context.log.info(s"Processing room release for reservation ${notification.reservationId}: " +
      s"${notification.roomName} released due to no-show by ${notification.employeeName}")

    context.pipeToSelf(emailService.sendAutoReleaseNotification(
      notification.employeeEmail,
      notification.employeeName,
      notification.roomName,
      notification.scheduledStartTime.toString
    )) {
      case Success(_) =>
        val processingTime = System.currentTimeMillis() - startTime
        NotificationProcessed(messageId, processingTime)
      case Failure(exception) =>
        val processingTime = System.currentTimeMillis() - startTime
        NotificationProcessingFailed(messageId, exception.getMessage, processingTime)
    }
  }

  /**
   * Processes administrative notifications by logging events for audit and monitoring.
   *
   * @param messageId unique message identifier
   * @param notification administrative notification details
   * @param startTime processing start timestamp
   * @param context actor context for logging and self-messaging
   */
  private def processAdminNotification(
    messageId: String,
    notification: AdminNotification,
    startTime: Long,
    context: akka.actor.typed.scaladsl.ActorContext[Command]
  ): Unit = {

    context.log.info(s"Admin notification - ${notification.eventType}: ${notification.details} " +
      s"for reservation ${notification.reservationId} in room ${notification.roomName}")

    val processingTime = System.currentTimeMillis() - startTime
    context.self ! NotificationProcessed(messageId, processingTime)
  }
}
