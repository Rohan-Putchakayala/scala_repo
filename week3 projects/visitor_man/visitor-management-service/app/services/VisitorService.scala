package services

import play.api.libs.json.Json.toJsFieldJsValueWrapper

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import models._
import repos._
import utils.{HashUtils, ValidationUtils}
import play.api.Configuration

import java.time.LocalDate
import slick.jdbc.JdbcProfile
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.Logging

@Singleton
class VisitorService @Inject()(
                                visitorRepo: VisitorRepo,
                                visitRepo: VisitRepo,
                                idProofRepo: IdProofRepo,
                                val dbConfigProvider: DatabaseConfigProvider,
                                config: Configuration,
                                employeeRepo: EmployeeRepo,
                                kafkaPublisher: KafkaPublisher
                              )(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] with Logging {

  import profile.api._

  /**
   * Performs visitor check-in with direct Kafka publishing.
   * Validates host + visitor, writes DB transaction, and publishes event directly to Kafka.
   */
  def checkin(json: JsValue): Future[JsValue] = {
    val visitorJs = (json \ "visitor").as[JsObject]

    val visitor = Visitor(
      None,
      (visitorJs \ "firstName").as[String],
      (visitorJs \ "lastName").as[String],
      (visitorJs \ "email").asOpt[String],
      (visitorJs \ "phone").asOpt[String]
    )

    val aadhaar = (json \ "idProof" \ "aadhaarNumber").as[String]
    val hostId  = (json \ "hostEmployeeId").as[Long]
    val purpose = (json \ "purpose").as[String]

    ValidationUtils.requireNonEmpty("firstName", visitor.firstName)
    ValidationUtils.requireNonEmpty("lastName", visitor.lastName)
    ValidationUtils.requireNonEmpty("purpose", purpose)
    ValidationUtils.requireAadhaar(aadhaar)

    val idProofHash = HashUtils.sha256(aadhaar)
    val checkinDate = LocalDate.now()
    val correlation = java.util.UUID.randomUUID().toString


    // 1) VALIDATE HOST - THIS IS A FUTURE
    employeeRepo.findById(hostId).flatMap {
      case Some(host) if host.role == "HOST" && host.status == "ACTIVE" =>
        // 2) BUILD THE TRANSACTION (DBIO INSIDE FOR)
        import profile.api._
        val tx: DBIO[(Long, Long)] = for {
          visitorId <- visitorRepo.upsert(visitor)
          visitId <- visitRepo.insert(
            Visit(
              None,
              visitorId,
              hostId,
              purpose,
              Some(checkinDate),
              None,
              "CHECKED_IN",
              None,
              Some(host.name),
              Some(host.email)
            )
          )
          _ <- idProofRepo.insert(IdProof(None, visitId, idProofHash))
        } yield (visitId, visitorId)

        // 3) RUN DB TRANSACTION
        db.run(tx.transactionally).flatMap { case (visitId, visitorId) =>

          // 4) PREPARE KAFKA EVENT PAYLOAD
          val payload = Json.obj(
            "eventType"      -> "visitor.checkin",
            "visitId"        -> visitId,
            "visitorId"      -> visitorId,
            "hostEmployeeId" -> hostId,
            "hostName"       -> host.name,
            "hostEmail"      -> host.email,
            "purpose"        -> purpose,
            "visitor"        -> visitorJs,
            "idProofHash"    -> idProofHash,
            "checkinDate"    -> checkinDate.toString,
            "correlationId"  -> correlation,
            "timestamp"      -> java.time.Instant.now().toString
          )

          // 5) PUBLISH TO KAFKA DIRECTLY
          kafkaPublisher.publishCheckinEvent(visitId, Json.stringify(payload)).map { _ =>
            logger.info(s"Successfully published check-in event for visitId: $visitId")

            Json.obj(
              "visitId"       -> visitId,
              "visitorId"     -> visitorId,
              "status"        -> "CHECKED_IN",
              "checkinDate"   -> checkinDate.toString,
              "correlationId" -> correlation
            )
          }.recover { kafkaEx =>
            logger.error(s"Failed to publish check-in event for visitId: $visitId", kafkaEx)
            // Still return success response since DB transaction succeeded
            // Consider implementing compensation logic or alerting here
            Json.obj(
              "visitId"       -> visitId,
              "visitorId"     -> visitorId,
              "status"        -> "CHECKED_IN",
              "checkinDate"   -> checkinDate.toString,
              "correlationId" -> correlation,
              "warning"       -> "Event publishing failed but check-in completed"
            )
          }
        }

      case Some(_) =>
        Future.failed(new IllegalArgumentException("Employee is not authorized as HOST"))

      case None =>
        Future.failed(new IllegalArgumentException("Invalid host employee ID"))
    }
  }

  /**
   * Performs visitor checkout with direct Kafka publishing.
   * Updates visit status and publishes checkout event directly to Kafka.
   */
  def checkout(visitId: Long): Future[Option[JsValue]] = {
    val checkoutDate = LocalDate.now()
    val correlation  = java.util.UUID.randomUUID().toString

    // 1) UPDATE DATABASE
    val updateAction = visitRepo.checkout(visitId, checkoutDate)

    db.run(updateAction).flatMap {
      case 0 =>
        // Visit not found
        Future.successful(None)

      case _ =>
        // 2) PREPARE KAFKA EVENT PAYLOAD
        val payload = Json.obj(
          "eventType"     -> "visitor.checkout",
          "visitId"       -> visitId,
          "checkoutDate"  -> checkoutDate.toString,
          "correlationId" -> correlation,
          "timestamp"     -> java.time.Instant.now().toString
        )

        // 3) PUBLISH TO KAFKA DIRECTLY
        kafkaPublisher.publishCheckoutEvent(visitId, Json.stringify(payload)).map { _ =>
          logger.info(s"Successfully published check-out event for visitId: $visitId")

          Some(Json.obj(
            "visitId"      -> visitId,
            "status"       -> "CHECKED_OUT",
            "checkoutDate" -> checkoutDate.toString,
            "correlationId" -> correlation
          ))
        }.recover { kafkaEx =>
          logger.error(s"Failed to publish check-out event for visitId: $visitId", kafkaEx)
          // Still return success response since DB transaction succeeded
          Some(Json.obj(
            "visitId"      -> visitId,
            "status"       -> "CHECKED_OUT",
            "checkoutDate" -> checkoutDate.toString,
            "correlationId" -> correlation,
            "warning"      -> "Event publishing failed but check-out completed"
          ))
        }
    }
  }

  /**
   * Fetches full visitor profile.
   * Includes visitor + all visits associated with that visitor.
   */
  def getVisitorDetails(visitorId: Long): Future[Option[JsValue]] = {
    for {
      visitorOpt <- visitorRepo.findById(visitorId)
      visits     <- visitRepo.findByVisitorId(visitorId)
    } yield {
      visitorOpt.map { v =>

        val visitsJson: Seq[JsObject] = visits.map { vs =>
          JsObject(Seq(
            "visitId"        -> JsNumber(BigDecimal(vs.id.getOrElse(0L))),
            "hostEmployeeId" -> JsNumber(BigDecimal(vs.hostEmployeeId)),
            "hostName"       -> vs.hostName.map(JsString).getOrElse(JsNull),
            "hostEmail"      -> vs.hostEmail.map(JsString).getOrElse(JsNull),
            "purpose"        -> JsString(vs.purpose),
            "checkinTime"    -> vs.checkinTime.map(dt => JsString(dt.toString)).getOrElse(JsNull),
            "checkoutTime"   -> vs.checkoutTime.map(dt => JsString(dt.toString)).getOrElse(JsNull),
            "status"         -> JsString(vs.status)
          ))
        }

        JsObject(Seq(
          "visitorId"  -> JsNumber(BigDecimal(visitorId)),
          "firstName"  -> JsString(v.firstName),
          "lastName"   -> JsString(v.lastName),
          "email"      -> v.email.map(JsString).getOrElse(JsNull),
          "phone"      -> v.phone.map(JsString).getOrElse(JsNull),
          "visits"     -> JsArray(visitsJson)
        ))
      }
    }
  }

  /**
   * Returns all active visits (CHECKED_IN only).
   */
  def getActiveVisits: Future[JsValue] = {
    visitRepo.findActiveVisits().map { visits =>

      val visitsJson: Seq[JsObject] = visits.map { v =>
        JsObject(Seq(
          "visitId"        -> JsNumber(BigDecimal(v.id.getOrElse(0L))),
          "visitorId"      -> JsNumber(BigDecimal(v.visitorId)),
          "hostEmployeeId" -> JsNumber(BigDecimal(v.hostEmployeeId)),
          "hostName"       -> v.hostName.map(JsString).getOrElse(JsNull),
          "hostEmail"      -> v.hostEmail.map(JsString).getOrElse(JsNull),
          "purpose"        -> JsString(v.purpose),
          "checkinTime"    -> v.checkinTime.map(dt => JsString(dt.toString)).getOrElse(JsNull)
        ))
      }

      JsArray(visitsJson)
    }
  }
}
