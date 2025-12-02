package repos

import models.Employee
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import tables.EmployeeTable

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for accessing employee records.
 *
 * Provides DB operations for fetching employee information,
 * primarily used to validate host employee details during visitor check-in.
 */
@Singleton
class EmployeeRepo @Inject()(
                              protected val dbConfigProvider: DatabaseConfigProvider
                            )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  private val employees = TableQuery[EmployeeTable]

  /**
   * Fetches an employee by ID.
   *
   * @param id Employee ID to lookup.
   * @return Future containing Some(Employee) if found, else None.
   */
  def findById(id: Long): Future[Option[Employee]] =
    db.run(employees.filter(_.id === id).result.headOption)
}
