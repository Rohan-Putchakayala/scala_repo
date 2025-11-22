package models

import java.time.Instant
import slick.jdbc.MySQLProfile.api._
import play.api.libs.json._

import java.sql.Timestamp

trait DateTimeSupport {

  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      instant => Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )
}

object DateTimeSupport extends DateTimeSupport
