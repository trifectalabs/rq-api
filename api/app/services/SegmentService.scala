package services

import java.util.UUID
import javax.inject.Inject

import com.trifectalabs.polyline.{LatLng, Polyline}
import com.trifectalabs.roadquality.v0.models.{Point, Segment, SegmentCreateForm, SegmentRating}
import com.vividsolutions.jts.geom.{ GeometryFactory, Coordinate, PrecisionModel, LineString }
import db.dao.{MapDao, MiniSegmentsDao, SegmentRatingsDao, SegmentsDao}
import models._
import org.joda.time.DateTime
import com.trifectalabs.roadquality.v0.models.{Segment, SegmentCreateForm, SegmentRating}
import scala.language.postfixOps
import scala.collection.JavaConversions._
import com.vividsolutions.jts.geom.Geometry
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import models.TileCacheExpiration
import com.trifectalabs.roadquality.v0.models.{ SegmentCreateForm, SegmentRating, Segment }
import db.dao.{ SegmentsDao, MapDao, SegmentRatingsDao, TileCacheExpirationsDao }


trait SegmentService {
  def createSegment(segmentCreateForm: SegmentCreateForm, userId: UUID): Future[Segment]
  def handleEndpointOfSegment(miniSegmentSplit: MiniSegmentSplit, segmentId: UUID): Future[Option[MiniSegmentToSegment]]
  def newOverlappingMiniSegments(polyline: String, segmentId: UUID): Future[Seq[MiniSegmentToSegment]]
}

class SegmentServiceImpl @Inject()
  (segmentsDao: SegmentsDao, miniSegmentsDao: MiniSegmentsDao, mapDao: MapDao, ratingsDao: SegmentRatingsDao, tileCacheExpirationsDao: TileCacheExpirationsDao)
  (implicit ec: ExecutionContext) extends SegmentService {
  implicit def latLng2Point(latLng: LatLng): Point = Point(lat = latLng.lat, lng = latLng.lng)

  def createSegment(segmentCreateForm: SegmentCreateForm, userId: UUID): Future[Segment] = {
    val segmentId = UUID.randomUUID()
    val segmentPoints: Seq[Point] = Polyline.decode(segmentCreateForm.polyline).map(latLng2Point)

    val startSplitsFutOpt: Future[Option[MiniSegmentSplit]] =
			miniSegmentsDao.miniSegmentSplitsFromPoint(segmentPoints.head, segmentPoints.tail.head)
    val endSplitsFutOpt: Future[Option[MiniSegmentSplit]] =
			miniSegmentsDao.miniSegmentSplitsFromPoint(segmentPoints.last, segmentPoints.init.last)
    val newOverlapMiniSegmentsFut = newOverlappingMiniSegments(segmentCreateForm.polyline, segmentId)

    (for {
      startSplitsOpt <- startSplitsFutOpt
      endSplitsOpt <- endSplitsFutOpt
			existingMiniSegments <- newOverlapMiniSegmentsFut
      segment <- segmentsDao.create(segmentId, segmentCreateForm, userId)
      } yield {
        val savedMiniSegments: Future[Seq[MiniSegmentToSegment]] = {
          (startSplitsOpt, endSplitsOpt) match {
            case (None, None) => Future(Nil)
            case (Some(start), None) => handleEndpointOfSegment(start, segmentId).map(p => (if (p.isEmpty) Nil else Seq(p.get)))
            case (None, Some(end)) => handleEndpointOfSegment(end, segmentId).map(p => (if (p.isEmpty) Nil else Seq(p.get)))
            case (Some(start), Some(end)) =>
              for {
                   start <- handleEndpointOfSegment(start, segmentId)
                   end <- handleEndpointOfSegment(end, segmentId)
              } yield {
                (if (start.isEmpty) Nil else Seq(start.get)) ++: (if (end.isEmpty) Nil else Seq(end.get))
              }
          }
        }

        // Any isn't good here, but we only care about the Future
        val miniSegmentsFuture: Future[Seq[Any]] = {
          savedMiniSegments.flatMap { previouslySavedMiniSegments =>
            Future.sequence(existingMiniSegments.map { existingMiniSegment =>
              if (!previouslySavedMiniSegments.map(s => s.miniSegmentId).contains(existingMiniSegment.miniSegmentId)) {
                (miniSegmentsDao.insert(existingMiniSegment))
              } else {
                Future()
              }
            })
          }
        }

        val ratingsFuture = ratingsDao.insert(
          SegmentRating(
            UUID.randomUUID(), segmentId, userId, segmentCreateForm.trafficRating, segmentCreateForm.surfaceRating,
            segmentCreateForm.surface, segmentCreateForm.pathType, DateTime.now(), DateTime.now()
          )
        ).flatMap { r =>
          ratingsDao.getBoundsFromRatings(r.createdAt).map { bounds =>
            println(bounds)
            tileCacheExpirationsDao.insert(TileCacheExpiration(bounds, DateTime.now(), None))
          }
        }
        Future.sequence(Seq(miniSegmentsFuture, ratingsFuture)).map( p => segment)
    }) flatMap identity
  }

  def newOverlappingMiniSegments(polyline: String, segmentId: UUID): Future[Seq[MiniSegmentToSegment]] = {
    mapDao.intersectionsSplitsFromSegment(polyline).flatMap { intersectionSplits =>
      Future.sequence {
        intersectionSplits.map { intersectionSplit =>
          miniSegmentsDao.overlappingMiniSegmentsFromPolyline(geomToPolyline(intersectionSplit)).map { existingMiniSegments =>
            existingMiniSegments match {
              case Nil => Seq(MiniSegmentToSegment(UUID.randomUUID(), intersectionSplit, segmentId))
              case Seq(existingMiniSegment) => Seq(existingMiniSegment.copy(segmentId = segmentId))
              case existingMiniSegments =>
                // This happens in situations where there are tiny intersections splits
                Logger.debug(s"Multiple mini segments found to be overlapping for intersection split. MiniSegmentIds: ${existingMiniSegments.map(s => s.miniSegmentId)}")
                existingMiniSegments.map(e => e.copy(segmentId = segmentId))
            }
          }
        }
      }
    } map (d => d.flatten)
  }

  def handleEndpointOfSegment(miniSegmentSplit: MiniSegmentSplit, segmentId: UUID): Future[Option[MiniSegmentToSegment]] = {
    if (miniSegmentSplit.firstLength >= 50.0 && miniSegmentSplit.secondLength >= 50.0) {
      val newMiniSegment = MiniSegmentToSegment(UUID.randomUUID(), miniSegmentSplit.first, segmentId)
			miniSegmentsDao.update(miniSegmentSplit.miniSegmentId, miniSegmentSplit.second)
      miniSegmentsDao.insert(newMiniSegment).map(p => Some(p))
    } else if (miniSegmentSplit.firstLength >= 50.0 && miniSegmentSplit.secondLength < 50.0) {
      val newMiniSegment = MiniSegmentToSegment(miniSegmentSplit.miniSegmentId, miniSegmentSplit.miniSegmentGeom, segmentId)
      miniSegmentsDao.insert(newMiniSegment).map(p => Some(p))
    } else if (miniSegmentSplit.firstLength < 50.0 && miniSegmentSplit.secondLength >= 50.0) {
      Future(None)
    } else {
      if (miniSegmentSplit.firstLength >= miniSegmentSplit.secondLength) {
        val newMiniSegment = MiniSegmentToSegment(miniSegmentSplit.miniSegmentId, miniSegmentSplit.miniSegmentGeom, segmentId)
        miniSegmentsDao.insert(newMiniSegment).map(p => Some(p))
      } else {
        Future(None)
      }
    }
  }

  private[this] def geomToPolyline(geom: Geometry): String = {
    Polyline.encode(geom.asInstanceOf[LineString].getCoordinates().toList.map(c => LatLng(c.y, c.x)))
  }

}
