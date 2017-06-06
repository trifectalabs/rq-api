package controllers

import javax.inject.Inject

import com.trifectalabs.roadquality.v0.models.Point
import com.trifectalabs.roadquality.v0.models.json._
import services.RoutingService
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import util.actions.Authenticated

import scala.concurrent.ExecutionContext

class MapRoutes @Inject() (routingService: RoutingService)(implicit ec: ExecutionContext) extends Controller {

  def get(start_lat: Double, start_lng: Double, end_lat: Double, end_lng: Double) = Authenticated.async { req =>
    routingService.generateRoute(start_lat, start_lng, end_lat, end_lng).map { r =>
        Ok(Json.toJson(r))
    }
  }

  def getSnap(lat: Double, lng: Double) = Authenticated.async { req =>
    routingService.snapPoint(Point(lat, lng)).map(p => Ok(Json.toJson(p)))
  }

  def post() = Authenticated.async(parse.json[Seq[Point]]) { req =>
    val points = req.body
    routingService.generateRoute(points).map { r =>
      Ok(Json.toJson(r))
    }
  }
}

