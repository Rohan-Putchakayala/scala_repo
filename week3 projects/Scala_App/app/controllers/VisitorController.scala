package controllers

import models._
import play.api.libs.json._
import play.api.mvc._
import services.VisitorService
import models.DateTimeSupport._

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VisitorController @Inject()(
                                   val controllerComponents: ControllerComponents,
                                   visitorService: VisitorService
                                 )(implicit ec: ExecutionContext) extends BaseController {
  def checkIn(): Action[Map[String, Seq[String]]] = Action.async(parse.formUrlEncoded) { request =>
    val formData = request.body

    val nameOpt = formData.get("name").flatMap(_.headOption)
    val emailOpt = formData.get("email").flatMap(_.headOption)
    val phoneOpt = formData.get("phoneNumber").flatMap(_.headOption)
    val companyOpt = formData.get("company").flatMap(_.headOption)
    val purposeOpt = formData.get("purposeOfVisit").flatMap(_.headOption)
    val hostEmailOpt = formData.get("hostEmployeeEmail").flatMap(_.headOption)
    val idProofNumberOpt = formData.get("idProofNumber").flatMap(_.headOption)

    (nameOpt, emailOpt, phoneOpt, purposeOpt, hostEmailOpt, idProofNumberOpt) match {
      case (Some(name), Some(email), Some(phone), Some(purpose), Some(hostEmail), Some(idProofNumber)) =>
        val checkInRequest = VisitorCheckInRequest(
          name = name,
          email = email,
          phoneNumber = phone,
          company = companyOpt,
          purposeOfVisit = purpose,
          hostEmployeeEmail = hostEmail,
          idProofNumber = idProofNumber
        )

        visitorService.checkInVisitor(checkInRequest).map { response =>
          Ok(Json.toJson(response))
        }.recover {
          case ex: IllegalArgumentException => BadRequest(Json.obj("error" -> ex.getMessage))
          case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to check in visitor: ${ex.getMessage}"))
        }

      case _ =>
        Future.successful(BadRequest(Json.obj("error" -> "Missing required fields: name, email, phoneNumber, purposeOfVisit, hostEmployeeEmail, idProofNumber")))
    }
  }
  def checkOut(checkInId: Long): Action[AnyContent] = Action.async {
    visitorService.checkOutVisitor(checkInId).map { response =>
      Ok(Json.toJson(response))
    }.recover {
      case ex: IllegalArgumentException => NotFound(Json.obj("error" -> ex.getMessage))
      case ex: IllegalStateException => BadRequest(Json.obj("error" -> ex.getMessage))
      case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to check out visitor: ${ex.getMessage}"))
    }
  }

  def getActiveVisitors(): Action[AnyContent] = Action.async {
    visitorService.getActiveCheckIns().map { records =>
      val result = records.map { case (checkIn, visitor) =>
        Json.obj(
          "checkInId" -> checkIn.id,
          "visitorId" -> visitor.id,
          "visitorName" -> visitor.name,
          "visitorEmail" -> visitor.email,
          "phoneNumber" -> visitor.phoneNumber,
          "company" -> visitor.company,
          "purposeOfVisit" -> visitor.purposeOfVisit,
          "hostEmployeeEmail" -> visitor.hostEmployeeEmail,
          // Use JSON Format for LocalDateTime
          "checkInTime" -> Json.toJson(checkIn.checkInTime),
          "status" -> checkIn.status
        )
      }
      Ok(Json.toJson(result))
    }.recover {
      case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to fetch active visitors: ${ex.getMessage}"))
    }
  }

  def getVisitor(id: Long): Action[AnyContent] = Action.async {
    visitorService.getVisitorById(id).map {
      case Some(visitor) => Ok(Json.toJson(visitor))
      case None => NotFound(Json.obj("error" -> "Visitor not found"))
    }.recover {
      case ex: Exception => InternalServerError(Json.obj("error" -> s"Failed to fetch visitor: ${ex.getMessage}"))
    }
  }
}
