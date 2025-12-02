package tables

import models.Visitor
import slick.jdbc.MySQLProfile.api._

class VisitorTable(tag: Tag) extends Table[Visitor](tag, "visitors") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def firstName = column[String]("first_name")
  def lastName = column[String]("last_name")
  def email = column[Option[String]]("email")
  def phone = column[Option[String]]("phone")

  def * = (id.?, firstName, lastName, email, phone) <> (Visitor.tupled, Visitor.unapply)
}
