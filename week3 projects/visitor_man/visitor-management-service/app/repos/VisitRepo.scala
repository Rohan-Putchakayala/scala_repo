package repos

import javax.inject._
import slick.jdbc.JdbcProfile
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import models._
import tables.VisitTable
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDate

/**
 * Repository for managing visit records.
 *
 * Provides DB operations for creating visits, updating checkout status,
 * and retrieving visits based on visitor or visit ID.
 */
@Singleton
class VisitRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
                         (implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  private val visits = TableQuery[VisitTable]

  def insert(v: Visit): DBIO[Long] =
    (visits returning visits.map(_.id)) += v

  /**
   * Marks a visit as checked out.
   *
   * @param visitId ID of the visit to update.
   * @param date Checkout date.
   * @return DBIO action producing number of rows updated (0 or 1).
   */
  def checkout(visitId: Long, date: LocalDate): DBIO[Int] =
    visits.filter(_.id === visitId)
      .map(v => (v.status, v.checkoutTime))
      .update(("CHECKED_OUT", Some(date)))

  def findByVisitorId(visitorId: Long): Future[Seq[Visit]] =
    db.run(visits.filter(_.visitorId === visitorId).result)

  def findActiveVisits(): Future[Seq[Visit]] =
    db.run(visits.filter(_.status === "CHECKED_IN").result)

  def findById(id: Long): Future[Option[Visit]] =
    db.run(visits.filter(_.id === id).result.headOption)
}
