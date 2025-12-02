package repositories

import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.{Instant, LocalDateTime}

/**
 * Data access for rooms and reservations using Slick and MySQL.
 * Includes serializable transactions for conflict-free reservation creation.
 */
@Singleton
class ReservationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  private val db = dbConfig.db

  private val rooms = TableQuery[RoomTable]
  private val reservations = TableQuery[ReservationTable]

  
  /** Inserts a new room and returns the persisted entity with generated id. */
  def createRoom(room: Room): Future[Room] = {
    val insertQuery = (rooms returning rooms.map(_.id) into ((roomRow, id) => roomRow.copy(id = Some(id)))) += room
    db.run(insertQuery)
  }

  /** Fetches a room by id. */
  def getRoomById(id: Long): Future[Option[Room]] = {
    db.run(rooms.filter(_.id === id).result.headOption)
  }

  /** Returns all rooms marked active. */
  def getAllActiveRooms(): Future[Seq[Room]] = {
    db.run(rooms.filter(_.isActive === true).result)
  }

  
  /** Inserts a reservation without conflict checks. Prefer the locked variant below. */
  def createReservation(reservation: Reservation): Future[Reservation] = {
    val insertQuery = (reservations returning reservations.map(_.id) into ((reservationRow, id) => reservationRow.copy(id = Some(id)))) += reservation
    db.run(insertQuery)
  }

  /**
   * Atomically checks for overlapping reservations and inserts when safe.
   * Uses serializable isolation to prevent race conditions under concurrent load.
   */
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

  /** Fetches a reservation by id. */
  def getReservationById(id: Long): Future[Option[Reservation]] = {
    db.run(reservations.filter(_.id === id).result.headOption)
  }

  /** Returns reservations overlapping the given time window for the room. */
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

  /** Joins active reservations with their rooms for controller responses. */
  def getActiveReservations(): Future[Seq[(Reservation, Room)]] = {
    val query = for {
      reservation <- reservations.filter(r => r.status.inSet(Seq("RESERVED", "IN_USE")))
      room <- rooms.filter(_.id === reservation.roomId)
    } yield (reservation, room)

    db.run(query.result)
  }

  /** Upcoming reservations within 15 minutes that haven't received a reminder yet. */
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

  /** Reservations past start by 15 minutes still in RESERVED state. */
  def getReservationsNeedingAutoRelease(currentTime: LocalDateTime): Future[Seq[Reservation]] = {
    val releaseTime = currentTime.minusMinutes(15)
    db.run(
      reservations
        .filter(r => r.status === "RESERVED")
        .filter(r => r.startTime <= releaseTime)
        .result
    )
  }

  /** Updates reservation status. */
  def updateReservationStatus(reservationId: Long, status: String): Future[Int] = {
    val updateQuery = reservations
      .filter(_.id === reservationId)
      .map(_.status)
      .update(status)
    db.run(updateQuery)
  }

  /** Marks notifications as attempted/sent for a reservation. */
  def updateNotificationsSent(reservationId: Long, sent: Boolean): Future[Int] = {
    val updateQuery = reservations
      .filter(_.id === reservationId)
      .map(_.notificationsSent)
      .update(sent)
    db.run(updateQuery)
  }

  /** Marks reminder as sent for a reservation. */
  def updateReminderSent(reservationId: Long, sent: Boolean): Future[Int] = {
    val updateQuery = reservations
      .filter(_.id === reservationId)
      .map(_.reminderSent)
      .update(sent)
    db.run(updateQuery)
  }

  /** Sets reservation status to AUTO_RELEASED. */
  def releaseReservation(reservationId: Long): Future[Int] = {
    updateReservationStatus(reservationId, "AUTO_RELEASED")
  }
}

