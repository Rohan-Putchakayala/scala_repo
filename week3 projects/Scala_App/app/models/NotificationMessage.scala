package models

import play.api.libs.json._

case class NotificationMessage(
                                visitorName: String,
                                hostEmail: String,
                                purpose: String,
                                time: String
                              )

object NotificationMessage {
  implicit val format: OFormat[NotificationMessage] = Json.format[NotificationMessage]
}
