package tables
import models.Visit
import slick.jdbc.MySQLProfile.api._

import java.time.{LocalDate, LocalDateTime}

class VisitTable(tag: Tag) extends Table[Visit](tag, "visits") {

  // LocalDate <-> SQL Date mapping
  implicit val localDateColumnType: BaseColumnType[LocalDate] =
    MappedColumnType.base[LocalDate, java.sql.Date](
      d => java.sql.Date.valueOf(d),
      s => s.toLocalDate
    )

  def id             = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def visitorId      = column[Long]("visitor_id")
  def hostEmployeeId = column[Long]("host_employee_id")
  def purpose        = column[String]("purpose")
  def checkinTime    = column[Option[LocalDate]]("checkin_time")
  def checkoutTime   = column[Option[LocalDate]]("checkout_time")
  def status         = column[String]("status")
  def createdBy      = column[Option[Long]]("created_by")
  def hostName       = column[Option[String]]("host_name")
  def hostEmail      = column[Option[String]]("host_email")

  def visitorFK =
    foreignKey("fk_visits_visitor", visitorId, TableQuery[VisitorTable])(_.id)

  def * =
    (
      id.?, visitorId, hostEmployeeId, purpose,
      checkinTime, checkoutTime, status, createdBy,
      hostName, hostEmail
    ).<>(Visit.tupled, Visit.unapply)
}
