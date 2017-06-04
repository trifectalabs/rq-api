package controllers

import javax.inject.Inject
import java.util.UUID

import play.twirl.api.Html
import play.api.mvc.{Action, Controller}
import play.api.libs.json.{ Json, JsObject }
import scala.concurrent.{ ExecutionContext, Future }
import db.dao.UsersDao
import com.trifectalabs.roadquality.v0.models.json._

import util.actions.AuthLoggingAction
import util.OAuth2
import util.JwtUtil

class Main @Inject() (jwtUtil: JwtUtil, authLoggingAction: AuthLoggingAction)(implicit ec: ExecutionContext) extends Controller {
  import authLoggingAction._

  def app(tokenOpt: Option[String]) = Action { request =>
    tokenOpt flatMap { token =>
      jwtUtil.decodeToken(token) map { user =>
        Ok(views.html.index((Json.toJson(user).as[JsObject] + ("token" -> Json.toJson(token))).toString))
      }
    } getOrElse(Ok(views.html.index("")))
  }

  def notFound(path: String) = Action { request =>
    NotFound(views.html.notFound())
  }

}
