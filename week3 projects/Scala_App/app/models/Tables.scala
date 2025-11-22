package models

import slick.jdbc.MySQLProfile.api._
import java.time.Instant
import java.sql.Timestamp

object Time {
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      instant => Timestamp.from(instant),
      ts => ts.toInstant
    )
}

class VisitorTable(tag: Tag) extends Table[Visitor](tag, "visitors") {

  import Time._   // <── REQUIRED

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def email = column[String]("email")
  def phoneNumber = column[String]("phone_number")
  def company = column[Option[String]]("company")
  def purposeOfVisit = column[String]("purpose_of_visit")
  def hostEmployeeEmail = column[String]("host_employee_email")
  def idProofNumber = column[String]("id_proof_number")
  def createdAt = column[Instant]("created_at")

  def * = (id.?, name, email, phoneNumber, company, purposeOfVisit,
    hostEmployeeEmail, idProofNumber, createdAt) <> (
    (Visitor.apply _).tupled,
    Visitor.unapply
  )
}

class CheckInRecordTable(tag: Tag) extends Table[CheckInRecord](tag, "check_in_records") {

  import Time._

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def visitorId = column[Long]("visitor_id")
  def checkInTime = column[Instant]("check_in_time")
  def checkOutTime = column[Option[Instant]]("check_out_time")
  def status = column[String]("status")
  def notificationsSent = column[Boolean]("notifications_sent")

  def * = (id.?, visitorId, checkInTime, checkOutTime, status, notificationsSent) <> (
    (CheckInRecord.apply _).tupled,
    CheckInRecord.unapply
  )

  def visitor =
    foreignKey("visitor_fk", visitorId, TableQuery[VisitorTable])(_.id, onDelete = ForeignKeyAction.Cascade)
}
