package models

import java.time.Instant
import play.api.libs.json.{Format, Json}

case class ITSupportNotification(
  visitorId: Long,
  visitorName: String,
  hostEmail: String,
  checkInTime: Instant
)

case class SecurityNotification(
  visitorId: Long,
  visitorName: String,
  idProofNumber: String,
  checkInTime: Instant
)

case class CheckOutNotification(
  checkInId: Long,
  visitorId: Long,
  checkOutTime: Instant
)

object NotificationJsonFormats {
  implicit val itSupportNotificationFormat: Format[ITSupportNotification] = Json.format[ITSupportNotification]
  implicit val securityNotificationFormat: Format[SecurityNotification] = Json.format[SecurityNotification]
  implicit val checkOutNotificationFormat: Format[CheckOutNotification] = Json.format[CheckOutNotification]
}
