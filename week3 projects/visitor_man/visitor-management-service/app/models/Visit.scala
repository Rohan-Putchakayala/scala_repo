package models

import java.time.LocalDate

case class Visit(
                  id: Option[Long],
                  visitorId: Long,
                  hostEmployeeId: Long,
                  purpose: String,
                  checkinTime: Option[LocalDate],
                  checkoutTime: Option[LocalDate],
                  status: String,
                  createdBy: Option[Long],
                  hostName: Option[String],
                  hostEmail: Option[String]
                )


