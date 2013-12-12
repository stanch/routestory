package net.routestory.display

import net.routestory.model.Story
import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model._
import scala.collection.JavaConversions._

object RouteManager {
  def getBearing(p1: LatLng, p2: LatLng) = {
    val (lat1, lat2, dlng) = (p1.latitude.toRadians, p2.latitude.toRadians, (p2.longitude - p1.longitude).toRadians)
    val y = Math.sin(dlng) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dlng)
    Math.atan2(y, x).toDegrees.toFloat
  }
}

class RouteManager(map: GoogleMap) {
  var route: Option[Polyline] = None

  def add(chapter: Story.Chapter) = route match {
    case Some(line) ⇒ line.setPoints(chapter.locations.map(_.coordinates))
    case None ⇒
      val routeOptions = new PolylineOptions
      chapter.locations.map(_.coordinates).foreach(routeOptions.add)
      routeOptions.width(7)
      routeOptions.color(Color.parseColor("#AD9A3D"))
      route = Some(map.addPolyline(routeOptions))
  }

  def remove() {
    route.foreach(_.remove())
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
    if (p.size < 2) 0f else RouteManager.getBearing(p(0), p(1))
  }
}
