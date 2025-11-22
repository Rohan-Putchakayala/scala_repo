package models

import java.time.Instant
import java.time.LocalDateTime
import play.api.libs.json._
import DateTimeSupport._









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




object Models {

  implicit val roomFormat: OFormat[Room] = Json.format[Room]
  implicit val reservationFormat: OFormat[Reservation] = Json.format[Reservation]
  implicit val reservationRequestReads: Reads[ReservationRequest] = ReservationRequest.reservationRequestReads
  implicit val reservationResponseFormat: OFormat[ReservationResponse] = Json.format[ReservationResponse]
  implicit val roomAvailabilityResponseFormat: OFormat[RoomAvailabilityResponse] = Json.format[RoomAvailabilityResponse]

  implicit val roomPreparationNotificationFormat: OFormat[RoomPreparationNotification] = Json.format[RoomPreparationNotification]
  implicit val reservationReminderNotificationFormat: OFormat[ReservationReminderNotification] = Json.format[ReservationReminderNotification]
  implicit val roomReleaseNotificationFormat: OFormat[RoomReleaseNotification] = Json.format[RoomReleaseNotification]
  implicit val adminNotificationFormat: OFormat[AdminNotification] = Json.format[AdminNotification]
}
