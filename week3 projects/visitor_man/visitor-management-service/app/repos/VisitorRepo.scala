package repos

import javax.inject.{Inject, Singleton}
import slick.jdbc.JdbcProfile
import play.api.db.slick.{HasDatabaseConfigProvider, DatabaseConfigProvider}
import models.Visitor
import tables.VisitorTable
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository responsible for CRUD operations on the `visitors` table.
 *
 * Used during check-in to upsert visitor records,
 * and later to fetch visitor details for profile and reporting.
 */
@Singleton
class VisitorRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
                           (implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  private val visitors = TableQuery[VisitorTable]

  /**
   * Finds a visitor by email or phone number.
   * Useful to detect returning visitors and avoid duplicate records.
   *
   * @param email Optional email value
   * @param phone Optional phone number
   * @return DBIO producing Some(Visitor) if a match exists, else None
   */
  def findByEmailOrPhone(email: Option[String], phone: Option[String]): DBIO[Option[Visitor]] = {
    visitors
      .filterOpt(email)((row, e) => row.email === e)
      .filterOpt(phone)((row, p) => row.phone === p)
      .result
      .headOption
  }

  /**
   * Inserts a new visitor record and returns the generated ID.
   *
   * @param v Visitor entity
   * @return DBIO[Long] auto-generated visitor ID
   */
  def insert(v: Visitor): DBIO[Long] =
    (visitors returning visitors.map(_.id)) += v

  def upsert(v: Visitor): DBIO[Long] = {
    findByEmailOrPhone(v.email, v.phone).flatMap {
      case Some(existing) =>
        visitors
          .filter(_.id === existing.id.get)
          .map(r => (r.firstName, r.lastName))
          .update((v.firstName, v.lastName))
          .map(_ => existing.id.get)

      case None =>
        insert(v.copy(id = None))
    }
  }

  /**
   * Fetches a visitor by ID.
   * This method uses Future because it's called directly from controllers.
   *
   * @param id Visitor ID
   * @return Future containing Some(visitor) if found, else None
   */
  def findById(id: Long): Future[Option[Visitor]] =
    db.run(visitors.filter(_.id === id).result.headOption)
}
