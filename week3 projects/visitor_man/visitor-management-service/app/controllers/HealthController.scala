package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json.Json

@Singleton
class HealthController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def ping: Action[AnyContent] = Action {
    Ok(
      Json.obj(
        "service" -> "visitor-reception-service",
        "status" -> "UP"
      )
    )
  }
}
