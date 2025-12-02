package models

import play.api.libs.json._
import java.time.{Instant, LocalDateTime}
import models.DateTimeSupport._

/**
 * Kafka notification message for room preparation events.
 * Published when a new reservation is created to notify facilities team.
 */
case class RoomPreparationNotification(
  reservationId: Long,
  roomId: Long,
  roomName: String,
  location: String,
  employeeName: String,
  employeeEmail: String,
  department: String,
  purpose: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  reservationTime: Instant
)

object RoomPreparationNotification {
  implicit val format: Format[RoomPreparationNotification] = Json.format[RoomPreparationNotification]
}

/**
 * Kafka notification message for reservation reminder events.
 * Published when a reservation is approaching to send reminder notifications.
 */
case class ReservationReminderNotification(
  reservationId: Long,
  roomId: Long,
  roomName: String,
  location: String,
  employeeName: String,
  employeeEmail: String,
  startTime: LocalDateTime
)

object ReservationReminderNotification {
  implicit val format: Format[ReservationReminderNotification] = Json.format[ReservationReminderNotification]
}

/**
 * Kafka notification message for room release events.
 * Published when a room is auto-released due to no-show.
 */
case class RoomReleaseNotification(
  reservationId: Long,
  roomId: Long,
  roomName: String,
  employeeName: String,
  employeeEmail: String,
  scheduledStartTime: LocalDateTime,
  releaseTime: Instant
)

object RoomReleaseNotification {
  implicit val format: Format[RoomReleaseNotification] = Json.format[RoomReleaseNotification]
}

/**
 * Kafka notification message for administrative events.
 * Published for audit trails, monitoring, and alerting systems.
 */
case class AdminNotification(
  eventType: String,
  reservationId: Long,
  roomId: Long,
  roomName: String,
  employeeEmail: String,
  timestamp: Instant,
  details: String
)

object AdminNotification {
  implicit val format: Format[AdminNotification] = Json.format[AdminNotification]
}
