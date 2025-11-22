package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import models._
import services.{EmailService, KafkaProducerService}
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class NotificationActor(emailService: EmailService, kafkaProducer: KafkaProducerService)
  extends Actor with ActorLogging {

  import NotificationActor._
  import context.dispatcher

  override def receive: Receive = {
    case SendCheckInNotifications(visitor, checkInRecord) =>
      val originalSender = sender()
      var notificationsSent = 0
      var failedReason: Option[String] = None

      def checkCompletion(): Unit = {
        notificationsSent += 1
        if (notificationsSent == 4) {
          failedReason match {
            case Some(error) =>
              originalSender ! NotificationFailure(error)
            case None =>
              originalSender ! NotificationSuccess("All check-in notifications sent successfully")
          }
        }
      }

      emailService.sendHostNotification(
        visitor.hostEmployeeEmail,
        visitor.name,
        visitor.email,
        visitor.phoneNumber,
        visitor.purposeOfVisit,
        checkInRecord.checkInTime.toString
      ).onComplete {
        case Success(_) =>
          log.info(s"Host notification sent for visitor ${visitor.id}")
          checkCompletion()
        case Failure(ex) =>
          failedReason = Some(s"Host email failed: ${ex.getMessage}")
          log.error(ex, "Failed to send host email")
          checkCompletion()
      }

      emailService.sendITWiFiCredentials(visitor.email, visitor.name)
        .onComplete {
          case Success(_) =>
            log.info(s"WiFi credentials sent to ${visitor.email}")
            checkCompletion()
          case Failure(ex) =>
            failedReason = Some(s"WiFi email failed: ${ex.getMessage}")
            log.error(ex, "Failed to send WiFi credentials")
            checkCompletion()
        }

      kafkaProducer.sendMessage(
        "visitor-it-notifications",
        s"it-${visitor.id.get}",
        ITSupportNotification(
          visitorId = visitor.id.get,
          visitorName = visitor.name,
          hostEmail = visitor.hostEmployeeEmail,
          checkInTime = checkInRecord.checkInTime
        )
      ).onComplete {
        case Success(_) =>
          log.info(s"IT notification sent for visitor ${visitor.id}")
          checkCompletion()
        case Failure(ex) =>
          failedReason = Some(s"IT notification failed: ${ex.getMessage}")
          log.error(ex, "Failed to send IT notification")
          checkCompletion()
      }

      kafkaProducer.sendMessage(
        "visitor-security-notifications",
        s"security-${visitor.id.get}",
        SecurityNotification(
          visitorId = visitor.id.get,
          visitorName = visitor.name,
          idProofNumber = visitor.idProofNumber,
          checkInTime = checkInRecord.checkInTime
        )
      ).onComplete {
        case Success(_) =>
          log.info(s"Security notification sent for visitor ${visitor.id}")
          checkCompletion()
        case Failure(ex) =>
          failedReason = Some(s"Security notification failed: ${ex.getMessage}")
          log.error(ex, "Failed to send security notification")
          checkCompletion()
      }

    case SendCheckOutNotifications(checkInId, visitorId) =>
      val originalSender = sender()

      kafkaProducer.sendMessage(
        "visitor-checkout-notifications",
        s"checkout-${checkInId}",
        CheckOutNotification(
          checkInId = checkInId,
          visitorId = visitorId,
          checkOutTime = Instant.now()
        )
      ).onComplete {
        case Success(_) =>
          log.info(s"Check-out notification sent for visitor $visitorId")
          originalSender ! NotificationSuccess("Check-out notification sent successfully")
        case Failure(ex) =>
          log.error(ex, "Failed to send check-out notification")
          originalSender ! NotificationFailure(s"Failed to send check-out notification: ${ex.getMessage}")
      }
  }
}

object NotificationActor {
  def props(emailService: EmailService, kafkaProducer: KafkaProducerService): Props =
    Props(new NotificationActor(emailService, kafkaProducer))

  sealed trait Command
  case class SendCheckInNotifications(visitor: Visitor, checkInRecord: CheckInRecord) extends Command
  case class SendCheckOutNotifications(checkInId: Long, visitorId: Long) extends Command

  sealed trait NotificationResult
  case class NotificationSuccess(message: String) extends NotificationResult
  case class NotificationFailure(error: String) extends NotificationResult
}