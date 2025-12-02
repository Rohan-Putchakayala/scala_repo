package services

import models._
import repositories.ReservationRepository
import play.api.Configuration

import java.time.{Instant, LocalDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Core business logic service for room reservations, availability checks,
 * and automated processing of reminders and releases.
 *
 * This service coordinates between the repository layer and Kafka messaging
 * to provide complete reservation lifecycle management.
 */
@Singleton
class ReservationService @Inject()(
  reservationRepository: ReservationRepository,
  kafkaProducerService: KafkaProducerService,
  config: Configuration
)(implicit ec: ExecutionContext) {

  private val reminderAdvanceMinutes: Int =
    config.getOptional[Int]("room.management.reminder.advance.minutes").getOrElse(15)
  private val autoReleaseDelayMinutes: Int =
    config.getOptional[Int]("room.management.auto.release.delay.minutes").getOrElse(15)

  /**
   * Creates a new room reservation with comprehensive validation and notification.
   *
   * Validates room existence, checks availability, persists the reservation,
   * and triggers asynchronous notifications via Kafka for downstream services.
   *
   * @param request the reservation request containing booking details
   * @return Future containing the reservation response with confirmation details
   * @throws IllegalArgumentException if room does not exist or input is invalid
   * @throws IllegalStateException if room is not available for the requested time
   */
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

      _ <- sendReservationNotifications(createdReservation, room)

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

  /**
   * Checks room availability for the specified time window.
   *
   * Validates room existence and checks for conflicting reservations
   * in the requested time range.
   *
   * @param roomId the room identifier to check availability for
   * @param startTime the start time of the requested booking window
   * @param endTime the end time of the requested booking window
   * @return Future containing availability response with next available time if blocked
   * @throws IllegalArgumentException if room does not exist
   */
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

  /**
   * Retrieves all active reservations with their associated room details.
   *
   * @return Future containing a sequence of reservation and room tuples
   */
  def getAllActiveReservations(): Future[Seq[(Reservation, Room)]] = {
    reservationRepository.getActiveReservations()
  }

  /**
   * Retrieves all active rooms in the system.
   *
   * @return Future containing a sequence of active rooms
   */
  def getAllRooms(): Future[Seq[Room]] = {
    reservationRepository.getAllActiveRooms()
  }

  /**
   * Processes pending reminder notifications for upcoming reservations.
   *
   * Identifies reservations that need reminder notifications (typically 15 minutes
   * before start time), publishes reminder events to Kafka, and updates the
   * reminder status in the database.
   *
   * @return Future containing the count of processed reminders
   */
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
            _ <- sendReminderNotification(reservation, room)
            _ <- reservationRepository.updateReminderSent(reservation.id.get, sent = true)
          } yield ()
        }
      )
    } yield reservationsNeedingReminder.size
  }

  /**
   * Processes auto-release of reservations that were not checked into.
   *
   * Identifies reservations that should be auto-released (typically 15 minutes
   * after start time with no check-in), updates their status, and publishes
   * release notifications to Kafka.
   *
   * @return Future containing the count of processed auto-releases
   */
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
            _ <- sendReleaseNotifications(reservation, room)
          } yield ()
        }
      )
    } yield reservationsNeedingRelease.size
  }

  /**
   * Sends comprehensive notifications for a new reservation.
   *
   * Publishes room preparation notification for facilities team and
   * administrative notification for audit and monitoring systems.
   *
   * @param reservation the created reservation
   * @param room the associated room details
   * @return Future indicating completion of notification sending
   */
  private def sendReservationNotifications(reservation: Reservation, room: Room): Future[Unit] = {
    for {
      _ <- kafkaProducerService.sendRoomPreparationNotification(
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
      )
      _ <- kafkaProducerService.sendAdminNotification(
        AdminNotification(
          eventType = "RESERVATION_CREATED",
          reservationId = reservation.id.get,
          roomId = room.id.get,
          roomName = room.name,
          employeeEmail = reservation.employeeEmail,
          timestamp = Instant.now(),
          details = s"New reservation created for ${reservation.employeeName} in ${room.name}"
        )
      )
    } yield ()
  }

  /**
   * Sends reminder notification for an upcoming reservation.
   *
   * @param reservation the reservation requiring a reminder
   * @param room the associated room details
   * @return Future indicating completion of reminder notification
   */
  private def sendReminderNotification(reservation: Reservation, room: Room): Future[Unit] = {
    kafkaProducerService.sendReservationReminder(
      ReservationReminderNotification(
        reservationId = reservation.id.get,
        roomId = room.id.get,
        roomName = room.name,
        location = room.location,
        employeeName = reservation.employeeName,
        employeeEmail = reservation.employeeEmail,
        startTime = reservation.startTime
      )
    ).map(_ => ())
  }

  /**
   * Sends comprehensive notifications for an auto-released reservation.
   *
   * Publishes room release notification for facilities team and
   * administrative notification for audit systems.
   *
   * @param reservation the reservation being released
   * @param room the associated room details
   * @return Future indicating completion of release notifications
   */
  private def sendReleaseNotifications(reservation: Reservation, room: Room): Future[Unit] = {
    for {
      _ <- kafkaProducerService.sendRoomReleaseNotification(
        RoomReleaseNotification(
          reservationId = reservation.id.get,
          roomId = room.id.get,
          roomName = room.name,
          employeeName = reservation.employeeName,
          employeeEmail = reservation.employeeEmail,
          scheduledStartTime = reservation.startTime,
          releaseTime = Instant.now()
        )
      )
      _ <- kafkaProducerService.sendAdminNotification(
        AdminNotification(
          eventType = "RESERVATION_AUTO_RELEASED",
          reservationId = reservation.id.get,
          roomId = room.id.get,
          roomName = room.name,
          employeeEmail = reservation.employeeEmail,
          timestamp = Instant.now(),
          details = s"Reservation auto-released for ${reservation.employeeName} in ${room.name}"
        )
      )
    } yield ()
  }
}
