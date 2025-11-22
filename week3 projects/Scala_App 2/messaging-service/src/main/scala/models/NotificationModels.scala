package models

import java.time.Instant
import java.time.LocalDateTime

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

case class ReservationReminderNotification(
  reservationId: Long,
  roomId: Long,
  roomName: String,
  location: String,
  employeeName: String,
  employeeEmail: String,
  startTime: LocalDateTime
)

case class RoomReleaseNotification(
  reservationId: Long,
  roomId: Long,
  roomName: String,
  employeeName: String,
  employeeEmail: String,
  scheduledStartTime: LocalDateTime,
  releaseTime: Instant
)

case class AdminNotification(
  eventType: String,
  reservationId: Long,
  roomId: Long,
  roomName: String,
  employeeEmail: String,
  timestamp: Instant,
  details: String
)
