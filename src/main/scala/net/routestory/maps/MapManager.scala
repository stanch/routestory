package net.routestory.maps

import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.{ LatLng, LatLngBounds, Polyline, PolylineOptions }
import com.javadocmd.simplelatlng.LatLngTool
import macroid.{ ActivityContext, AppContext }
import net.routestory.data.Story.Chapter
import net.routestory.util.Implicits._

import scala.collection.JavaConversions._

class MapManager(map: GoogleMap, displaySize: List[Int])(implicit ctx: ActivityContext, appCtx: AppContext) {
  var route: Option[Polyline] = None

  def addRoute(chapter: Chapter) = route match {
    case Some(line) ⇒ line.setPoints(chapter.locations.map(_.coordinates: LatLng))
    case None ⇒
      val routeOptions = new PolylineOptions
      chapter.locations.map(_.coordinates: LatLng).foreach(routeOptions.add)
      routeOptions.width(7)
      routeOptions.color(Color.parseColor("#AD9A3D"))
      route = Some(map.addPolyline(routeOptions))
  }

  def removeRoute() {
    route.foreach(_.remove())
    route = None
  }

  def bounds = route map { r ⇒
    val boundsBuilder = LatLngBounds.builder()
    r.getPoints.foreach(boundsBuilder.include)
    boundsBuilder.build()
  }

  def points = route.map(_.getPoints)

  def start = points.map(_.head)

  def end = points.map(_.last)

  def startBearing = points.map { p ⇒
    import net.routestory.util.Implicits._
    if (p.size < 2) 0f else LatLngTool.initialBearing(p(0), p(1))
  }
}
