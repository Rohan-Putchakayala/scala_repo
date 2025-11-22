package models

import java.time.{Instant, LocalDateTime}
import slick.jdbc.MySQLProfile.api._
import play.api.libs.json._

import java.sql.Timestamp

trait DateTimeSupport {
  
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      instant => Timestamp.from(instant),      
      timestamp => timestamp.toInstant         
    )

  
  implicit val localDateTimeColumnType: BaseColumnType[LocalDateTime] =
    MappedColumnType.base[LocalDateTime, Timestamp](
      localDateTime => Timestamp.valueOf(localDateTime),      
      timestamp => timestamp.toLocalDateTime                   
    )

  
  implicit val instantFormat: Format[Instant] = new Format[Instant] {
    def reads(json: JsValue): JsResult[Instant] = json.validate[String].map(Instant.parse)
    def writes(instant: Instant): JsValue = JsString(instant.toString)
  }

  
  implicit val localDateTimeFormat: Format[LocalDateTime] = new Format[LocalDateTime] {
    def reads(json: JsValue): JsResult[LocalDateTime] = json.validate[String].map(LocalDateTime.parse)
    def writes(localDateTime: LocalDateTime): JsValue = JsString(localDateTime.toString)
  }
}

object DateTimeSupport extends DateTimeSupport
