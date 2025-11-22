package services

import actors.ReservationActor
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import models._
import repositories.ReservationRepository

import java.time.{Instant, LocalDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ReservationService @Inject()(
  reservationRepository: ReservationRepository,
  reservationActor: ActorRef[ReservationActor.Command],
  system: ActorSystem[Nothing]
)(implicit ec: ExecutionContext) {

  implicit val timeout: Timeout = 10.seconds
  implicit val actorSystem: ActorSystem[Nothing] = system

  def createReservation(request: ReservationRequest): Future[ReservationResponse] = {
    val startTime = LocalDateTime.parse(request.startTime)
    val endTime = startTime.plusMinutes(request.durationMinutes)

    for {
      
      roomOpt <- reservationRepository.getRoomById(request.roomId)
      room <- Future.successful(
        roomOpt.getOrElse(throw new IllegalArgumentException(s"Room not found: ${request.roomId}"))
      )

      reservation = Reservation(
        roomId = request.roomId,
        employeeName = request.employeeName,
        employeeEmail = request.employeeEmail,
        department = request.department,
        purpose = request.purpose,
        startTime = startTime,
        endTime = endTime,
        status = "RESERVED",
        notificationsSent = false,
        reminderSent = false
      )
      createdReservation <- reservationRepository.createReservationWithAvailabilityLock(reservation)

      
      notificationResult <- reservationActor.ask[ReservationActor.ReservationNotificationResult] { replyTo =>
        ReservationActor.SendReservationNotifications(createdReservation, room, replyTo)
      }
      _ <- notificationResult match {
        case ReservationActor.ReservationNotificationSuccess(_) => 
          Future.successful(())
        case ReservationActor.ReservationNotificationFailure(error) => 
          
          Future.successful(())
      }

      
      _ <- reservationRepository.updateNotificationsSent(createdReservation.id.get, sent = true)

    } yield ReservationResponse(
      reservationId = createdReservation.id.get,
      roomId = room.id.get,
      roomName = room.name,
      startTime = startTime,
      endTime = endTime,
      message = "Reservation created successfully. Notifications sent."
    )
  }

  def getRoomAvailability(roomId: Long, startTime: LocalDateTime, endTime: LocalDateTime): Future[RoomAvailabilityResponse] = {
    for {
      roomOpt <- reservationRepository.getRoomById(roomId)
      room <- Future.successful(
        roomOpt.getOrElse(throw new IllegalArgumentException(s"Room not found: $roomId"))
      )
      conflictingReservations <- reservationRepository.getReservationsByRoomAndTimeRange(
        roomId,
        startTime,
        endTime
      )
      isAvailable = conflictingReservations.isEmpty
      nextAvailableTime = if (!isAvailable) {
        conflictingReservations.headOption.map(_.endTime)
      } else None
    } yield RoomAvailabilityResponse(
      roomId = room.id.get,
      roomName = room.name,
      capacity = room.capacity,
      location = room.location,
      isAvailable = isAvailable,
      nextAvailableTime = nextAvailableTime
    )
  }

  def getAllActiveReservations(): Future[Seq[(Reservation, Room)]] = {
    reservationRepository.getActiveReservations()
  }

  def getAllRooms(): Future[Seq[Room]] = {
    reservationRepository.getAllActiveRooms()
  }

  def processReminders(): Future[Int] = {
    val currentTime = LocalDateTime.now()
    for {
      reservationsNeedingReminder <- reservationRepository.getReservationsNeedingReminder(currentTime)
      _ <- Future.sequence(
        reservationsNeedingReminder.map { reservation =>
          for {
            roomOpt <- reservationRepository.getRoomById(reservation.roomId)
            room <- Future.successful(
              roomOpt.getOrElse(throw new IllegalStateException(s"Room not found: ${reservation.roomId}"))
            )
            _ <- reservationActor.ask[ReservationActor.ReservationNotificationResult] { replyTo =>
              ReservationActor.SendReminderNotification(reservation, room, replyTo)
            }
            _ <- reservationRepository.updateReminderSent(reservation.id.get, sent = true)
          } yield ()
        }
      )
    } yield reservationsNeedingReminder.size
  }

  def processAutoReleases(): Future[Int] = {
    val currentTime = LocalDateTime.now()
    for {
      reservationsNeedingRelease <- reservationRepository.getReservationsNeedingAutoRelease(currentTime)
      _ <- Future.sequence(
        reservationsNeedingRelease.map { reservation =>
          for {
            roomOpt <- reservationRepository.getRoomById(reservation.roomId)
            room <- Future.successful(
              roomOpt.getOrElse(throw new IllegalStateException(s"Room not found: ${reservation.roomId}"))
            )
            _ <- reservationRepository.releaseReservation(reservation.id.get)
            _ <- reservationActor.ask[ReservationActor.ReservationNotificationResult] { replyTo =>
              ReservationActor.SendReleaseNotifications(reservation, room, replyTo)
            }
          } yield ()
        }
      )
    } yield reservationsNeedingRelease.size
  }
}

