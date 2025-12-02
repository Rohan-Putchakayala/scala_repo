package controllers

import play.api.mvc._
import play.api.libs.json._

import javax.inject._
import utils.JwtUtility

/**
 * Authentication controller for issuing JWT tokens.
 *
 * Validates username/password against application config and
 * returns a signed JWT token if authentication succeeds.
 */
@Singleton
class AuthController @Inject()(
                                cc: ControllerComponents,
                                config: play.api.Configuration,
                                jwtUtility: JwtUtility
                              ) extends AbstractController(cc) {

  private val validUser = config.get[String]("auth.username")
  private val validPass = config.get[String]("auth.password")

  /**
   * POST /login
   *
   * Validates credentials and returns a JWT token if correct.
   *
   * Request JSON:
   * { "username": "...", "password": "..." }
   *
   * Responses:
   *  - 200 OK with { "token": "<jwt>" }
   *  - 401 Unauthorized if credentials are invalid
   */
  def login = Action(parse.json) { req =>
    val username = (req.body \ "username").as[String]
    val password = (req.body \ "password").as[String]

    if (username == validUser && password == validPass) {
      val token = jwtUtility.createToken(username)
      Ok(Json.obj("token" -> token))
    } else {
      Unauthorized(Json.obj("error" -> "Invalid username or password"))
    }
  }
}
