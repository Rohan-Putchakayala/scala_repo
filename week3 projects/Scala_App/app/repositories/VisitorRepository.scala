package repositories

import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class VisitorRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  private val db = dbConfig.db

  private val visitors = TableQuery[VisitorTable]
  private val checkInRecords = TableQuery[CheckInRecordTable]

  def createVisitor(visitor: Visitor): Future[Visitor] = {
    println(visitor)
    val insertQuery = (visitors returning visitors.map(_.id) into ((visitorRow, id) => visitorRow.copy(id = Some(id)))) += visitor
    db.run(insertQuery)
  }

  def getVisitorById(id: Long): Future[Option[Visitor]] = {
    db.run(visitors.filter(_.id === id).result.headOption)
  }

  def createCheckIn(checkInRecord: CheckInRecord): Future[CheckInRecord] = {
    val insertQuery = (checkInRecords returning checkInRecords.map(_.id) into ((record, id) => record.copy(id = Some(id)))) += checkInRecord
    db.run(insertQuery)
  }

  def getCheckInById(id: Long): Future[Option[CheckInRecord]] = {
    db.run(checkInRecords.filter(_.id === id).result.headOption)
  }

  def getActiveCheckInByVisitorId(visitorId: Long): Future[Option[CheckInRecord]] = {
    db.run(
      checkInRecords
        .filter(r => r.visitorId === visitorId && r.status === "CHECKED_IN")
        .sortBy(_.checkInTime.desc)
        .result
        .headOption
    )
  }

  def updateCheckOut(checkInId: Long, checkOutTime: Instant): Future[Int] = {
    val updateQuery = checkInRecords
      .filter(_.id === checkInId)
      .map(r => (r.checkOutTime, r.status))
      .update((Some(checkOutTime), "CHECKED_OUT"))
    db.run(updateQuery)
  }

  def updateNotificationsSent(checkInId: Long, sent: Boolean): Future[Int] = {
    val updateQuery = checkInRecords
      .filter(_.id === checkInId)
      .map(_.notificationsSent)
      .update(sent)
    db.run(updateQuery)
  }

  def getAllActiveCheckIns(): Future[Seq[(CheckInRecord, Visitor)]] = {
    val query = for {
      record <- checkInRecords.filter(_.status === "CHECKED_IN")
      visitor <- visitors.filter(_.id === record.visitorId)
    } yield (record, visitor)

    db.run(query.result)
  }
}
