package controllers

import javax.inject._
import play.api.mvc._

import play.api.data._
import play.api.data.Forms._

case class $model;format="Camel"$Data(name: String, age: Int)
object $model;format="Camel"$Data {
  def unapply(data: $model;format="Camel"$Data): Option[(String, Int)] = Some((data.name, data.age))
}

class $model;format="Camel"$Controller @Inject()(mcc: MessagesControllerComponents) extends MessagesAbstractController(mcc) {

  val $model;format="camel"$Form = Form(
    mapping(
      "name" -> text,
      "age" -> number
    )($model;format="Camel"$Data.apply)($model;format="Camel"$Data.unapply)
  )

  def $model;format="camel"$Get() = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.$model;format="camel"$.form($model;format="camel"$Form))
  }

  def $model;format="camel"$Post() = Action { implicit request: MessagesRequest[AnyContent] =>
    $model;format="camel"$Form.bindFromRequest().fold(
      formWithErrors => {
        BadRequest(views.html.$model;format="camel"$.form(formWithErrors))
      },
      $model;format="camel"$Data => {

        Redirect(routes.$model;format="Camel"$Controller.$model;format="camel"$Get()).flashing("success" -> ("Successful " + $model;format="camel"$Data.toString))
      }
    )
  }
}
