package tables

import models.Employee
import slick.jdbc.MySQLProfile.api._
//import slick.model.Table
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

class EmployeeTable(tag: Tag) extends Table[Employee](tag, "employees") {
    def id         = column[Long]("id", O.PrimaryKey)
    def name       = column[String]("name")
    def email      = column[String]("email")
    def department = column[Option[String]]("department")
    def role       = column[String]("role")
    def status     = column[String]("status")

    def * = (id, name, email, department, role, status) <> (Employee.tupled, Employee.unapply)
}
