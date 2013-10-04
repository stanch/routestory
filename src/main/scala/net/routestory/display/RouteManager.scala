package net.routestory.display

import net.routestory.model.Story
import android.graphics.Color

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model._

import scala.collection.JavaConversions._

object RouteManager {
  def getBearing(p1: LatLng, p2: LatLng) = {
    val y = Math.sin(p2.longitude - p1.longitude) * Math.cos(p2.latitude)
    val x = Math.cos(p1.latitude) * Math.sin(p2.latitude) - Math.sin(p2.latitude) * Math.cos(p2.latitude) * Math.cos(p2.longitude - p1.longitude)
    (Math.atan2(y, x) * 180 / Math.PI).toFloat
  }
}

class RouteManager(map: GoogleMap, story: Story) {
  var route: Option[Polyline] = None

  def isEmpty = route.exists(_.getPoints.size > 0)

  def init(): RouteManager = if (story.locations.isEmpty) {
    route = None
    this
  } else {
    val routeOptions = new PolylineOptions
    story.locations foreach { l ⇒
      routeOptions.add(l.asLatLng)
    }
    routeOptions.width(7)
    routeOptions.color(Color.parseColor("#AD9A3D"))
    route = Some(map.addPolyline(routeOptions))
    this
  }

  def getBounds = route map { r ⇒
    val boundsBuilder = LatLngBounds.builder()
    r.getPoints foreach { point ⇒
      boundsBuilder.include(point)
    }
    boundsBuilder.build()
  }

  def getPoints = route.map(_.getPoints)

  def getStart = getPoints.map(_.head)

  def getEnd = getPoints.map(_.last)

  def getStartBearing = getPoints.map { p ⇒
    if (p.size < 2) 0f else RouteManager.getBearing(p(0), p(1))
  }

  def update() {
    route.getOrElse(init())
    route.foreach(_.setPoints(story.locations.map(_.asLatLng)))
  }

  def remove() {
    route.foreach(_.remove())
  }
}
