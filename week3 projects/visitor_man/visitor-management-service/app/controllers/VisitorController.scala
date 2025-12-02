package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.VisitorService
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging

/**
 * Combined Visitor Management Controller that handles both reception validation
 * and visitor processing functionality. This controller:
 *
 * 1. Validates incoming JSON from receptionist UI
 * 2. Processes visitor check-in/checkout business logic
 * 3. Publishes events directly to Kafka
 * 4. Returns appropriate responses to the client
 */
@Singleton
class VisitorController @Inject()(
                                   cc: ControllerComponents,
                                   service: VisitorService,
                                   secured: actions.SecuredAction
                                 )(implicit ec: ExecutionContext)
  extends AbstractController(cc) with Logging {

  /**
   * Handles visitor check-in with comprehensive validation.
   * Validates required fields and Aadhaar format before processing.
   *
   * @return Created with visit details or BadRequest with validation errors
   */
  def checkin: Action[JsValue] = secured.async(parse.json) { request =>
    val json = request.body

    // Extract and validate required fields
    val visitor      = (json \ "visitor").asOpt[JsObject]
    val hostId       = (json \ "hostEmployeeId").asOpt[Long]
    val purpose      = (json \ "purpose").asOpt[String]
    val aadhaarOpt   = (json \ "idProof" \ "aadhaarNumber").asOpt[String]

    // Comprehensive validation
    if (visitor.isEmpty) {
      logger.warn("Check-in request missing visitor information")
      Future.successful(BadRequest(Json.obj(
        "error" -> "Missing visitor information",
        "code" -> "MISSING_VISITOR"
      )))
    }
    else if (hostId.isEmpty) {
      logger.warn("Check-in request missing host employee ID")
      Future.successful(BadRequest(Json.obj(
        "error" -> "Missing host employee ID",
        "code" -> "MISSING_HOST_ID"
      )))
    }
    else if (purpose.isEmpty || purpose.get.trim.isEmpty) {
      logger.warn("Check-in request missing or empty purpose")
      Future.successful(BadRequest(Json.obj(
        "error" -> "Purpose is required and cannot be empty",
        "code" -> "MISSING_PURPOSE"
      )))
    }
    else if (aadhaarOpt.isEmpty) {
      logger.warn("Check-in request missing Aadhaar number")
      Future.successful(BadRequest(Json.obj(
        "error" -> "Aadhaar number is required for ID proof",
        "code" -> "MISSING_AADHAAR"
      )))
    }
    else if (!aadhaarOpt.get.matches("^[0-9]{12}$")) {
      logger.warn(s"Invalid Aadhaar format provided: ${aadhaarOpt.get}")
      Future.successful(BadRequest(Json.obj(
        "error" -> "Invalid Aadhaar number format (must be exactly 12 digits)",
        "code" -> "INVALID_AADHAAR_FORMAT"
      )))
    }
    else {
      // Validate visitor object fields
      val visitorObj = visitor.get
      val firstName = (visitorObj \ "firstName").asOpt[String]
      val lastName = (visitorObj \ "lastName").asOpt[String]

      if (firstName.isEmpty || firstName.get.trim.isEmpty) {
        logger.warn("Check-in request missing visitor first name")
        Future.successful(BadRequest(Json.obj(
          "error" -> "Visitor first name is required",
          "code" -> "MISSING_FIRST_NAME"
        )))
      }
      else if (lastName.isEmpty || lastName.get.trim.isEmpty) {
        logger.warn("Check-in request missing visitor last name")
        Future.successful(BadRequest(Json.obj(
          "error" -> "Visitor last name is required",
          "code" -> "MISSING_LAST_NAME"
        )))
      }
      else {
        logger.info(s"Processing check-in for visitor: ${firstName.get} ${lastName.get}, Host ID: ${hostId.get}")

        service.checkin(json).map { result =>
          logger.info(s"Check-in successful for visitId: ${(result \ "visitId").as[Long]}")
          Created(result)
        }.recover { ex =>
          logger.error(s"Check-in failed: ${ex.getMessage}", ex)
          ex.getMessage match {
            case msg if msg.contains("not authorized as HOST") =>
              BadRequest(Json.obj(
                "error" -> "Employee is not authorized as HOST",
                "code" -> "UNAUTHORIZED_HOST"
              ))
            case msg if msg.contains("Invalid host employee ID") =>
              BadRequest(Json.obj(
                "error" -> "Invalid host employee ID",
                "code" -> "INVALID_HOST_ID"
              ))
            case _ =>
              InternalServerError(Json.obj(
                "error" -> "Internal server error during check-in",
                "code" -> "CHECKIN_FAILED"
              ))
          }
        }
      }
    }
  }

  /**
   * Handles visitor checkout by visit ID.
   * Updates visit status and publishes checkout event.
   *
   * @param id The visit ID to check out
   * @return Ok with checkout details or NotFound if visit doesn't exist
   */
  def checkout(id: Long): Action[AnyContent] = secured.async { request =>
    logger.info(s"Processing checkout for visitId: $id")

    service.checkout(id).map {
      case Some(result) =>
        logger.info(s"Checkout successful for visitId: $id")
        Ok(result)
      case None =>
        logger.warn(s"Checkout failed - visit not found: $id")
        NotFound(Json.obj(
          "error" -> "Visit not found or already checked out",
          "code" -> "VISIT_NOT_FOUND"
        ))
    }.recover { ex =>
      logger.error(s"Checkout failed for visitId $id: ${ex.getMessage}", ex)
      InternalServerError(Json.obj(
        "error" -> "Internal server error during checkout",
        "code" -> "CHECKOUT_FAILED"
      ))
    }
  }

  /**
   * Fetches full visitor profile including all visits.
   *
   * @param id The visitor ID
   * @return Ok with visitor details or NotFound if visitor doesn't exist
   */
  def getVisitor(id: Long): Action[AnyContent] = secured.async { request =>
    logger.debug(s"Fetching visitor details for visitorId: $id")

    service.getVisitorDetails(id).map {
      case Some(visitorData) =>
        Ok(visitorData)
      case None =>
        logger.warn(s"Visitor not found: $id")
        NotFound(Json.obj(
          "error" -> "Visitor not found",
          "code" -> "VISITOR_NOT_FOUND"
        ))
    }.recover { ex =>
      logger.error(s"Failed to fetch visitor $id: ${ex.getMessage}", ex)
      InternalServerError(Json.obj(
        "error" -> "Internal server error while fetching visitor",
        "code" -> "FETCH_VISITOR_FAILED"
      ))
    }
  }

  /**
   * Returns all currently active visits (CHECKED_IN status only).
   *
   * @return Ok with array of active visits
   */
  def activeVisits: Action[AnyContent] = secured.async { request =>
    logger.debug("Fetching active visits")

    service.getActiveVisits.map { visits =>
      Ok(visits)
    }.recover { ex =>
      logger.error(s"Failed to fetch active visits: ${ex.getMessage}", ex)
      InternalServerError(Json.obj(
        "error" -> "Internal server error while fetching active visits",
        "code" -> "FETCH_ACTIVE_VISITS_FAILED"
      ))
    }
  }
}
