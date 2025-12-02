package utils

import play.api.Configuration
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtJson}
import play.api.libs.json._

import java.time.Clock
import scala.util.{Success, Failure, Try}
import javax.inject.{Inject, Singleton}

/**
 * JWT Utility class for creating and validating JWT tokens
 * Used for authentication in the visitor management system
 */
@Singleton
class JwtUtility @Inject()(config: Configuration) {

  private val jwtSecret: String = config.get[String]("jwt.secret")
  private val expirationHours: Int = config.getOptional[Int]("jwt.expiration.hours").getOrElse(24)
  private val algorithm = JwtAlgorithm.HS256
  private implicit val clock: Clock = Clock.systemUTC()

  /**
   * Creates a JWT token for the given username
   *
   * @param username The username to encode in the token
   * @param role Optional role for the user (default: "USER")
   * @return JWT token string
   */
  def createToken(username: String, role: String = "USER"): String = {
    val now = clock.instant().getEpochSecond
    val expiration = now + (expirationHours * 3600)

    val payload = Json.obj(
      "sub" -> username,
      "username" -> username,
      "role" -> role,
      "iat" -> now,
      "exp" -> expiration
    )

    JwtJson.encode(payload, jwtSecret, algorithm)
  }

  /**
   * Validates a JWT token and returns the claim if valid
   *
   * @param token The JWT token to validate
   * @return Some(JsValue) if valid, None if invalid
   */
  def validateToken(token: String): Option[JsValue] = {
    JwtJson.decodeJson(token, jwtSecret, Seq(algorithm)) match {
      case Success(claim) => Some(claim)
      case Failure(_) => None
    }
  }

  /**
   * Extracts username from a valid JWT token
   *
   * @param token The JWT token
   * @return Some(username) if token is valid, None otherwise
   */
  def extractUsername(token: String): Option[String] = {
    validateToken(token).flatMap { claim =>
      (claim \ "username").asOpt[String]
    }
  }

  /**
   * Extracts role from a valid JWT token
   *
   * @param token The JWT token
   * @return Some(role) if token is valid and contains role, None otherwise
   */
  def extractRole(token: String): Option[String] = {
    validateToken(token).flatMap { claim =>
      (claim \ "role").asOpt[String]
    }
  }

  /**
   * Checks if a token is expired
   *
   * @param token The JWT token to check
   * @return true if expired, false if still valid
   */
  def isTokenExpired(token: String): Boolean = {
    validateToken(token) match {
      case Some(claim) =>
        (claim \ "exp").asOpt[Long] match {
          case Some(exp) => clock.instant().getEpochSecond > exp
          case None => false
        }
      case None => true
    }
  }

  /**
   * Refreshes a token by creating a new one with updated expiration
   *
   * @param token The current JWT token
   * @return Some(new token) if current token is valid, None otherwise
   */
  def refreshToken(token: String): Option[String] = {
    for {
      username <- extractUsername(token)
      role <- extractRole(token)
    } yield createToken(username, role)
  }
}

object JwtUtility {
  /**
   * Static methods for backward compatibility
   */
  def createToken(username: String)(implicit jwtUtility: JwtUtility): String = {
    jwtUtility.createToken(username)
  }

  def validateToken(token: String)(implicit jwtUtility: JwtUtility): Option[JsValue] = {
    jwtUtility.validateToken(token)
  }
}
