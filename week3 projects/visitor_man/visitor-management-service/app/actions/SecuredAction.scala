package actions

import javax.inject.Inject
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import utils.JwtUtility
/**
 * Action builder that secures routes using JWT authentication.
 *
 * Extracts the `Authorization: Bearer <token>` header, validates it
 * using JwtUtility, and only allows the request to proceed if valid.
 *
 * Used to protect API endpoints that require authentication.
 */
class SecuredAction @Inject()(parser: BodyParsers.Default, jwtUtility: JwtUtility)(implicit ec: ExecutionContext)
  extends ActionBuilderImpl(parser) {

  /**
   * Intercepts incoming requests and checks for a valid JWT token.
   *
   * Behaviour:
   *  - If header contains `Bearer <token>` and token is valid → proceeds to the controller action.
   *  - If token is missing → returns 401 Unauthorized.
   *  - If token is invalid or expired → returns 401 Unauthorized.
   */
  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {

    val maybeToken = request.headers.get("Authorization").map(_.replace("Bearer ", ""))

    maybeToken match {
      case Some(token) =>
        jwtUtility.validateToken(token) match {
          case Some(_) => block(request)
          case None    => Future.successful(Results.Unauthorized("Invalid or expired token"))
        }
      case None =>
        Future.successful(Results.Unauthorized("Missing Authorization token"))
    }
  }
}
