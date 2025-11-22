package repositories

import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.{Instant, LocalDateTime}

@Singleton
class ReservationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  private val db = dbConfig.db

  private val rooms = TableQuery[RoomTable]
  private val reservations = TableQuery[ReservationTable]

  
  def createRoom(room: Room): Future[Room] = {
    val insertQuery = (rooms returning rooms.map(_.id) into ((roomRow, id) => roomRow.copy(id = Some(id)))) += room
    db.run(insertQuery)
  }

  def getRoomById(id: Long): Future[Option[Room]] = {
    db.run(rooms.filter(_.id === id).result.headOption)
  }

  def getAllActiveRooms(): Future[Seq[Room]] = {
    db.run(rooms.filter(_.isActive === true).result)
  }

  
  def createReservation(reservation: Reservation): Future[Reservation] = {
    val insertQuery = (reservations returning reservations.map(_.id) into ((reservationRow, id) => reservationRow.copy(id = Some(id)))) += reservation
    db.run(insertQuery)
  }

  def createReservationWithAvailabilityLock(reservation: Reservation): Future[Reservation] = {
    val checkConflicts = reservations
      .filter(r => r.roomId === reservation.roomId)
      .filter(r => r.status.inSet(Seq("RESERVED", "IN_USE")))
      .filter(r =>
        (r.startTime <= reservation.startTime && r.endTime > reservation.startTime) ||
        (r.startTime < reservation.endTime && r.endTime >= reservation.endTime) ||
        (r.startTime >= reservation.startTime && r.endTime <= reservation.endTime)
      )
      .result

    val insertReservation = (reservations returning reservations.map(_.id) into ((reservationRow, id) => reservationRow.copy(id = Some(id)))) += reservation

    val action = for {
      conflicts <- checkConflicts
      created <- conflicts match {
        case seq if seq.nonEmpty => DBIO.failed(new IllegalStateException("Room is already reserved during this time"))
        case _ => insertReservation
      }
    } yield created

    db.run(action.transactionally.withTransactionIsolation(slick.jdbc.TransactionIsolation.Serializable))
  }

  def getReservationById(id: Long): Future[Option[Reservation]] = {
    db.run(reservations.filter(_.id === id).result.headOption)
  }

  def getReservationsByRoomAndTimeRange(
    roomId: Long,
    startTime: LocalDateTime,
    endTime: LocalDateTime
  ): Future[Seq[Reservation]] = {
    db.run(
      reservations
        .filter(r => r.roomId === roomId)
        .filter(r => r.status.inSet(Seq("RESERVED", "IN_USE")))
        .filter(r => 
          (r.startTime <= startTime && r.endTime > startTime) ||
          (r.startTime < endTime && r.endTime >= endTime) ||
          (r.startTime >= startTime && r.endTime <= endTime)
        )
        .result
    )
  }

  def getActiveReservations(): Future[Seq[(Reservation, Room)]] = {
    val query = for {
      reservation <- reservations.filter(r => r.status.inSet(Seq("RESERVED", "IN_USE")))
      room <- rooms.filter(_.id === reservation.roomId)
    } yield (reservation, room)

    db.run(query.result)
  }

  def getReservationsNeedingReminder(currentTime: LocalDateTime): Future[Seq[Reservation]] = {
    val reminderTime = currentTime.plusMinutes(15)
    db.run(
      reservations
        .filter(r => r.status === "RESERVED")
        .filter(r => r.reminderSent === false)
        .filter(r => r.startTime <= reminderTime && r.startTime > currentTime)
        .result
    )
  }

  def getReservationsNeedingAutoRelease(currentTime: LocalDateTime): Future[Seq[Reservation]] = {
    val releaseTime = currentTime.minusMinutes(15)
    db.run(
      reservations
        .filter(r => r.status === "RESERVED")
        .filter(r => r.startTime <= releaseTime)
        .result
    )
  }

  def updateReservationStatus(reservationId: Long, status: String): Future[Int] = {
    val updateQuery = reservations
      .filter(_.id === reservationId)
      .map(_.status)
      .update(status)
    db.run(updateQuery)
  }

  def updateNotificationsSent(reservationId: Long, sent: Boolean): Future[Int] = {
    val updateQuery = reservations
      .filter(_.id === reservationId)
      .map(_.notificationsSent)
      .update(sent)
    db.run(updateQuery)
  }

  def updateReminderSent(reservationId: Long, sent: Boolean): Future[Int] = {
    val updateQuery = reservations
      .filter(_.id === reservationId)
      .map(_.reminderSent)
      .update(sent)
    db.run(updateQuery)
  }

  def releaseReservation(reservationId: Long): Future[Int] = {
    updateReservationStatus(reservationId, "AUTO_RELEASED")
  }
}

