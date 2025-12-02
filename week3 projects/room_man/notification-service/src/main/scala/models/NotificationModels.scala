package models

import play.api.libs.json._
import java.time.{Instant, LocalDateTime}

/**
 * Kafka notification message for room preparation events.
 * Consumed by facilities team to prepare rooms before meetings.
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
  implicit val localDateTimeFormat: Format[LocalDateTime] = Format[LocalDateTime](
    Reads.localDateTimeReads,
    Writes.temporalWrites[LocalDateTime, String]()
  )
  implicit val instantFormat: Format[Instant] = Format[Instant](
    Reads.instantReads,
    Writes.temporalWrites[Instant, String]()
  )
  implicit val format: Format[RoomPreparationNotification] = Json.format[RoomPreparationNotification]
}

/**
 * Kafka notification message for reservation reminder events.
 * Consumed to send email reminders to employees before their meetings.
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
  implicit val localDateTimeFormat: Format[LocalDateTime] = Format[LocalDateTime](
    Reads.localDateTimeReads,
    Writes.temporalWrites[LocalDateTime, String]()
  )
  implicit val format: Format[ReservationReminderNotification] = Json.format[ReservationReminderNotification]
}

/**
 * Kafka notification message for room release events.
 * Consumed when rooms are auto-released due to no-shows.
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
  implicit val localDateTimeFormat: Format[LocalDateTime] = Format[LocalDateTime](
    Reads.localDateTimeReads,
    Writes.temporalWrites[LocalDateTime, String]()
  )
  implicit val instantFormat: Format[Instant] = Format[Instant](
    Reads.instantReads,
    Writes.temporalWrites[Instant, String]()
  )
  implicit val format: Format[RoomReleaseNotification] = Json.format[RoomReleaseNotification]
}

/**
 * Kafka notification message for administrative events.
 * Consumed for audit trails, monitoring, and alerting systems.
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
  implicit val instantFormat: Format[Instant] = Format[Instant](
    Reads.instantReads,
    Writes.temporalWrites[Instant, String]()
  )
  implicit val format: Format[AdminNotification] = Json.format[AdminNotification]
}

/**
 * Internal message wrapper for Kafka consumer processing.
 */
sealed trait NotificationMessage

case class RoomPreparationMessage(notification: RoomPreparationNotification) extends NotificationMessage
case class ReservationReminderMessage(notification: ReservationReminderNotification) extends NotificationMessage
case class RoomReleaseMessage(notification: RoomReleaseNotification) extends NotificationMessage
case class AdminMessage(notification: AdminNotification) extends NotificationMessage

/**
 * Processing result for notification handling.
 */
sealed trait NotificationResult
case class NotificationSuccess(messageId: String, processingTime: Long) extends NotificationResult
case class NotificationFailure(messageId: String, error: String, retryable: Boolean) extends NotificationResult
