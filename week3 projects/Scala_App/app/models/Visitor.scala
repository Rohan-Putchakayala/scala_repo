package models

import play.api.libs.json._
import java.time.Instant
import DateTimeSupport._

case class Visitor(
  id: Option[Long] = None,
  name: String,
  email: String,
  phoneNumber: String,
  company: Option[String],
  purposeOfVisit: String,
  hostEmployeeEmail: String,
  idProofNumber: String,
  createdAt: Instant = Instant.now()
)

object Visitor {
  implicit val visitorFormat: Format[Visitor] = Json.format[Visitor]
}

case class CheckInRecord(
  id: Option[Long] = None,
  visitorId: Long,
  checkInTime: Instant = Instant.now(),
  checkOutTime: Option[Instant] = None,
  status: String = "CHECKED_IN", // CHECKED_IN, CHECKED_OUT
  notificationsSent: Boolean = false
)

object CheckInRecord {
  implicit val checkInRecordFormat: Format[CheckInRecord] = Json.format[CheckInRecord]
}

case class VisitorCheckInRequest(
  name: String,
  email: String,
  phoneNumber: String,
  company: Option[String],
  purposeOfVisit: String,
  hostEmployeeEmail: String,
  idProofNumber: String
)

object VisitorCheckInRequest {
  implicit val checkInRequestFormat: Format[VisitorCheckInRequest] = Json.format[VisitorCheckInRequest]
}

case class VisitorCheckInResponse(
  visitorId: Long,
  checkInId: Long,
  checkInTime: Instant,
  message: String
)

object VisitorCheckInResponse {
  implicit val checkInResponseFormat: Format[VisitorCheckInResponse] = Json.format[VisitorCheckInResponse]
}

case class VisitorCheckOutResponse(
  checkInId: Long,
  checkOutTime: Instant,
  message: String
)

object VisitorCheckOutResponse {
  implicit val checkOutResponseFormat: Format[VisitorCheckOutResponse] = Json.format[VisitorCheckOutResponse]
}

case class ITSupportNotification(
  visitorId: Long,
  visitorName: String,
  hostEmail: String,
  checkInTime: Instant
)

object ITSupportNotification {
  implicit val itSupportNotificationFormat: Format[ITSupportNotification] = Json.format[ITSupportNotification]
}

case class SecurityNotification(
  visitorId: Long,
  visitorName: String,
  idProofNumber: String,
  checkInTime: Instant
)

object SecurityNotification {
  implicit val securityNotificationFormat: Format[SecurityNotification] = Json.format[SecurityNotification]
}

case class CheckOutNotification(
  checkInId: Long,
  visitorId: Long,
  checkOutTime: Instant
)

object CheckOutNotification {
  implicit val checkOutNotificationFormat: Format[CheckOutNotification] = Json.format[CheckOutNotification]
}
