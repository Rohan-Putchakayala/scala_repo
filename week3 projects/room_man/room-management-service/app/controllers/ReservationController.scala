package controllers

import models._
import play.api.libs.json._
import play.api.mvc._
import services.ReservationService
import models.DateTimeSupport._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

/**
 * REST API controller for room reservation management operations.
 *
 * Provides endpoints for creating reservations, checking availability,
 * retrieving active reservations, and managing room data. Handles
 * request validation, error responses, and coordinates with the
 * reservation service for business logic execution.
 */
@Singleton
class ReservationController @Inject()(
  val controllerComponents: ControllerComponents,
  reservationService: ReservationService
)(implicit ec: ExecutionContext) extends BaseController {

  /**
   * Creates a new room reservation from JSON request payload.
   *
   * Validates the incoming JSON against the ReservationRequest schema,
   * delegates to the service layer for business logic processing,
   * and returns appropriate HTTP responses with reservation details.
   *
   * @return Action that processes reservation creation requests
   */
  def createReservation(): Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[ReservationRequest].fold(
      errors => {
        Future.successful(BadRequest(Json.obj(
          "error" -> "Invalid request format",
          "details" -> JsError.toJson(errors)
        )))
      },
      reservationRequest => {
        reservationService.createReservation(reservationRequest).map { response =>
          Ok(Json.toJson(response))
        }.recover {
          case ex: IllegalArgumentException =>
            BadRequest(Json.obj("error" -> ex.getMessage))
          case ex: IllegalStateException =>
            Conflict(Json.obj("error" -> ex.getMessage))
          case ex: Exception =>
            InternalServerError(Json.obj("error" -> s"Failed to create reservation: ${ex.getMessage}"))
        }
      }
    )
  }

  /**
   * Checks room availability for a specified time window.
   *
   * Extracts query parameters for room ID, start time, and end time,
   * validates the date format, and returns availability information
   * including next available time if the room is currently booked.
   *
   * @return Action that processes availability check requests
   */
  def checkAvailability(): Action[AnyContent] = Action.async { request =>
    val roomIdOpt = request.getQueryString("roomId").map(_.toLong)
    val startTimeOpt = request.getQueryString("startTime")
    val endTimeOpt = request.getQueryString("endTime")

    (roomIdOpt, startTimeOpt, endTimeOpt) match {
      case (Some(roomId), Some(startTimeStr), Some(endTimeStr)) =>
        try {
          val startTime = java.time.LocalDateTime.parse(startTimeStr)
          val endTime = java.time.LocalDateTime.parse(endTimeStr)
          reservationService.getRoomAvailability(roomId, startTime, endTime).map { response =>
            Ok(Json.toJson(response))
          }.recover {
            case ex: IllegalArgumentException =>
              BadRequest(Json.obj("error" -> ex.getMessage))
            case ex: Exception =>
              InternalServerError(Json.obj("error" -> s"Failed to check availability: ${ex.getMessage}"))
          }
        } catch {
          case _: Exception =>
            Future.successful(BadRequest(Json.obj(
              "error" -> "Invalid date format. Use ISO format (e.g., 2024-01-15T10:00:00)"
            )))
        }
      case _ =>
        Future.successful(BadRequest(Json.obj(
          "error" -> "Missing required query parameters: roomId, startTime, endTime"
        )))
    }
  }

  /**
   * Retrieves all currently active reservations with room details.
   *
   * Returns a comprehensive list of active reservations joined with
   * their corresponding room information for dashboard and monitoring
   * purposes.
   *
   * @return Action that returns active reservations with room details
   */
  def getActiveReservations(): Action[AnyContent] = Action.async {
    reservationService.getAllActiveReservations().map { reservations =>
      val result = reservations.map { case (reservation, room) =>
        Json.obj(
          "reservationId" -> reservation.id,
          "roomId" -> room.id,
          "roomName" -> room.name,
          "location" -> room.location,
          "employeeName" -> reservation.employeeName,
          "employeeEmail" -> reservation.employeeEmail,
          "department" -> reservation.department,
          "purpose" -> reservation.purpose,
          "startTime" -> Json.toJson(reservation.startTime),
          "endTime" -> Json.toJson(reservation.endTime),
          "status" -> reservation.status
        )
      }
      Ok(Json.toJson(result))
    }.recover {
      case ex: Exception =>
        InternalServerError(Json.obj("error" -> s"Failed to fetch active reservations: ${ex.getMessage}"))
    }
  }

  /**
   * Retrieves all available rooms in the system.
   *
   * Returns a list of all rooms that are currently active and
   * available for booking, including their capacity and amenities
   * information.
   *
   * @return Action that returns all available rooms
   */
  def getAllRooms(): Action[AnyContent] = Action.async {
    reservationService.getAllRooms().map { rooms =>
      Ok(Json.toJson(rooms))
    }.recover {
      case ex: Exception =>
        InternalServerError(Json.obj("error" -> s"Failed to fetch rooms: ${ex.getMessage}"))
    }
  }

  /**
   * Manually triggers processing of pending reminder notifications.
   *
   * Typically called by scheduled jobs or administrative interfaces
   * to process reminders for upcoming reservations that haven't
   * been reminded yet.
   *
   * @return Action that processes pending reminders and returns count
   */
  def processReminders(): Action[AnyContent] = Action.async {
    reservationService.processReminders().map { count =>
      Ok(Json.obj("message" -> s"Processed $count reminders"))
    }.recover {
      case ex: Exception =>
        InternalServerError(Json.obj("error" -> s"Failed to process reminders: ${ex.getMessage}"))
    }
  }

  /**
   * Manually triggers processing of auto-release operations.
   *
   * Typically called by scheduled jobs to automatically release
   * reservations that haven't been checked into within the
   * configured time window after their start time.
   *
   * @return Action that processes auto-releases and returns count
   */
  def processAutoReleases(): Action[AnyContent] = Action.async {
    reservationService.processAutoReleases().map { count =>
      Ok(Json.obj("message" -> s"Processed $count auto-releases"))
    }.recover {
      case ex: Exception =>
        InternalServerError(Json.obj("error" -> s"Failed to process auto-releases: ${ex.getMessage}"))
    }
  }

  /**
   * Health check endpoint for service monitoring and load balancer probes.
   *
   * Returns a simple status response indicating that the service is running
   * and can process requests. Used by monitoring systems and load balancers
   * to determine service availability.
   *
   * @return Action that returns service health status
   */
  def health(): Action[AnyContent] = Action {
    Ok(Json.obj(
      "service" -> "room-management-service",
      "status" -> "healthy",
      "timestamp" -> java.time.Instant.now().toString,
      "version" -> "1.0.0"
    ))
  }
}
