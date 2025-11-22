package models

import slick.jdbc.MySQLProfile.api._
import java.time.Instant
import java.time.LocalDateTime
import java.sql.Timestamp

object RoomTime {
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      instant => Timestamp.from(instant),
      ts => ts.toInstant
    )

  implicit val localDateTimeColumnType: BaseColumnType[LocalDateTime] =
    MappedColumnType.base[LocalDateTime, Timestamp](
      localDateTime => Timestamp.valueOf(localDateTime),
      ts => ts.toLocalDateTime
    )
}

class RoomTable(tag: Tag) extends Table[Room](tag, "rooms") {

  import RoomTime._

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def capacity = column[Int]("capacity")
  def location = column[String]("location")
  def amenities = column[Option[String]]("amenities")
  def isActive = column[Boolean]("is_active")
  def createdAt = column[Instant]("created_at")

  def * = (id.?, name, capacity, location, amenities, isActive, createdAt) <> (
    (Room.apply _).tupled,
    Room.unapply
  )
}

class ReservationTable(tag: Tag) extends Table[Reservation](tag, "reservations") {

  import RoomTime._

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def roomId = column[Long]("room_id")
  def employeeName = column[String]("employee_name")
  def employeeEmail = column[String]("employee_email")
  def department = column[String]("department")
  def purpose = column[String]("purpose")
  def startTime = column[LocalDateTime]("start_time")
  def endTime = column[LocalDateTime]("end_time")
  def status = column[String]("status")
  def notificationsSent = column[Boolean]("notifications_sent")
  def reminderSent = column[Boolean]("reminder_sent")
  def createdAt = column[Instant]("created_at")

  def * = (id.?, roomId, employeeName, employeeEmail, department, purpose,
    startTime, endTime, status, notificationsSent, reminderSent, createdAt) <> (
    (Reservation.apply _).tupled,
    Reservation.unapply
  )

  def room =
    foreignKey("room_fk", roomId, TableQuery[RoomTable])(_.id, onDelete = ForeignKeyAction.Cascade)
}

