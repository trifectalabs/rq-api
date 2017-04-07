package controllers

import java.util.UUID
import javax.inject.Inject

import com.trifectalabs.roadquality.v0.models.User
import com.trifectalabs.roadquality.v0.models.json._
import com.trifectalabs.roadquality.v0.Bindables
import db.dao.UsersDao
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import util.actions.AuthLoggingAction

import scala.concurrent.ExecutionContext

class Users @Inject() (usersDao: UsersDao, authLoggingAction: AuthLoggingAction)(implicit ec: ExecutionContext) extends Controller {
  import authLoggingAction._

  def getByUserId(user_id: UUID) = AuthLoggingAction.async {
    usersDao.getById(user_id).map(s => Ok(Json.toJson(s)))
	}

  def getEmailByUserEmail(user_email: String) = AuthLoggingAction.async {
    usersDao.findByEmail(user_email).map(s => Ok(Json.toJson(s)))
  }

  def deleteByUserId(user_id: _root_.java.util.UUID) = AuthLoggingAction.async { implicit request =>
    usersDao.delete(user_id).map(s => Ok(Json.toJson(s)))
	}

  def patchSexByUserIdAndSex(user_id: _root_.java.util.UUID, sex: String) = AuthLoggingAction.async { implicit request =>
    usersDao.updateSex(user_id, sex).map(s => Ok(Json.toJson(s)))
  }

  def patchBirthdateByUserIdAndBirthdate(user_id: _root_.java.util.UUID, birthdate: _root_.org.joda.time.DateTime) = AuthLoggingAction.async { implicit request =>
    usersDao.updateBirthdate(user_id, birthdate).map(s => Ok(Json.toJson(s)))
	}

}