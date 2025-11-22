package models

import play.api.libs.json._
import java.time.Instant
import java.time.LocalDateTime
import DateTimeSupport._
import java.time.Duration

case class Room(
  id: Option[Long] = None,
  name: String,
  capacity: Int,
  location: String,
  amenities: Option[String],
  isActive: Boolean = true,
  createdAt: Instant = Instant.now()
)

object Room {
  implicit val roomFormat: Format[Room] = Json.format[Room]
}

case class Reservation(
  id: Option[Long] = None,
  roomId: Long,
  employeeName: String,
  employeeEmail: String,
  department: String,
  purpose: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  status: String = "RESERVED", 
  notificationsSent: Boolean = false,
  reminderSent: Boolean = false,
  createdAt: Instant = Instant.now()
)

object Reservation {
  implicit val reservationFormat: Format[Reservation] = Json.format[Reservation]
}

case class ReservationRequest(
  roomId: Long,
  employeeName: String,
  employeeEmail: String,
  department: String,
  purpose: String,
  startTime: String, 
  durationMinutes: Int
)

object ReservationRequest {
  implicit val reservationRequestReads: Reads[ReservationRequest] = Reads { json =>
    val roomIdOpt = (json \ "roomId").asOpt[Long]
    val purposeOpt = (json \ "purpose").asOpt[String]
    val startStrOpt = (json \ "startTime").asOpt[String]
    val employeeEmailOpt = (json \ "employeeEmail").asOpt[String].orElse((json \ "reservedBy").asOpt[String])
    val employeeNameOpt = (json \ "employeeName").asOpt[String]
    val departmentOpt = (json \ "department").asOpt[String]
    val durationOpt = (json \ "durationMinutes").asOpt[Int]
    val endOpt = (json \ "endTime").asOpt[String].map(LocalDateTime.parse)

    (roomIdOpt, purposeOpt, startStrOpt, employeeEmailOpt) match {
      case (Some(roomId), Some(purpose), Some(startStr), Some(employeeEmail)) =>
        val start = LocalDateTime.parse(startStr)
        val computedDurationOpt = durationOpt.orElse(endOpt.map(end => Duration.between(start, end).toMinutes.toInt))
        computedDurationOpt match {
          case Some(d) if d > 0 =>
            val name = employeeNameOpt.getOrElse(employeeEmail.takeWhile(_ != '@'))
            val dept = departmentOpt.getOrElse("General")
            JsSuccess(ReservationRequest(roomId, name, employeeEmail, dept, purpose, startStr, d))
          case _ => JsError("Invalid reservation duration")
        }
      case _ => JsError("Missing required fields")
    }
  }
}

case class ReservationResponse(
  reservationId: Long,
  roomId: Long,
  roomName: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  message: String
)

object ReservationResponse {
  implicit val reservationResponseFormat: Format[ReservationResponse] = Json.format[ReservationResponse]
}

case class RoomAvailabilityResponse(
  roomId: Long,
  roomName: String,
  capacity: Int,
  location: String,
  isAvailable: Boolean,
  nextAvailableTime: Option[LocalDateTime]
)

object RoomAvailabilityResponse {
  implicit val roomAvailabilityResponseFormat: Format[RoomAvailabilityResponse] = Json.format[RoomAvailabilityResponse]
}

