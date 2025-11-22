package services

import actors.NotificationActor
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import models._
import repositories.VisitorRepository
import javax.inject.{Inject, Named, Singleton}
import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VisitorService @Inject()(
                                visitorRepository: VisitorRepository,
                                @Named("notification-actor") notificationActor: ActorRef
                              )(implicit ec: ExecutionContext) {

  implicit val timeout: Timeout = 30.seconds

  def checkInVisitor(
                      request: VisitorCheckInRequest
                    ): Future[VisitorCheckInResponse] = {
    val visitor = Visitor(
      name = request.name,
      email = request.email,
      phoneNumber = request.phoneNumber,
      company = request.company,
      purposeOfVisit = request.purposeOfVisit,
      hostEmployeeEmail = request.hostEmployeeEmail,
      idProofNumber = request.idProofNumber
    )

    for {
      createdVisitor <- visitorRepository.createVisitor(visitor)

      checkInRecord = CheckInRecord(
        visitorId = createdVisitor.id.get,
        checkInTime = Instant.now(),
        status = "CHECKED_IN",
        notificationsSent = false
      )

      createdCheckIn <- visitorRepository.createCheckIn(checkInRecord)

      notificationResult <- (notificationActor ?
        NotificationActor.SendCheckInNotifications(createdVisitor, createdCheckIn))
        .mapTo[NotificationActor.NotificationResult]
        .recover {
          case ex =>
            NotificationActor.NotificationFailure(s"Notification failed: ${ex.getMessage}")
        }

      _ <- notificationResult match {
        case NotificationActor.NotificationSuccess(_) =>
          visitorRepository.updateNotificationsSent(createdCheckIn.id.get, sent = true)
        case NotificationActor.NotificationFailure(error) =>
          println(s"Failed to send notifications: $error")
          Future.successful(())
      }

    } yield VisitorCheckInResponse(
      visitorId = createdVisitor.id.get,
      checkInId = createdCheckIn.id.get,
      checkInTime = createdCheckIn.checkInTime,
      message = "Visitor checked in successfully"
    )
  }

  def checkOutVisitor(checkInId: Long): Future[VisitorCheckOutResponse] = {
    val checkOutTime = Instant.now()

    for {
      checkInOpt <- visitorRepository.getCheckInById(checkInId)
      checkIn = checkInOpt.getOrElse(
        throw new IllegalArgumentException(s"Check-in record not found: $checkInId")
      )

      _ <- if (checkIn.status == "CHECKED_OUT") {
        Future.failed(new IllegalStateException("Visitor already checked out"))
      } else Future.successful(())

      _ <- visitorRepository.updateCheckOut(checkInId, checkOutTime)

      visitorOpt <- visitorRepository.getVisitorById(checkIn.visitorId)
      _ = visitorOpt.getOrElse(throw new IllegalArgumentException("Visitor not found"))

      notificationResult <- (notificationActor ?
        NotificationActor.SendCheckOutNotifications(checkInId, checkIn.visitorId))
        .mapTo[NotificationActor.NotificationResult]
        .recover {
          case ex =>
            println(s"Check-out notification failed: ${ex.getMessage}")
            NotificationActor.NotificationSuccess("Check-out completed but notifications failed")
        }

    } yield VisitorCheckOutResponse(
      checkInId = checkInId,
      checkOutTime = checkOutTime,
      message = "Visitor checked out successfully"
    )
  }

  def getActiveCheckIns(): Future[Seq[(CheckInRecord, Visitor)]] = {
    visitorRepository.getAllActiveCheckIns()
  }

  def getVisitorById(id: Long): Future[Option[Visitor]] = {
    visitorRepository.getVisitorById(id)
  }
}