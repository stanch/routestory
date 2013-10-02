package net.routestory.display

import net.routestory.model.Story
import android.graphics.Color

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model._

import scala.collection.JavaConversions._

class RouteManager(map: GoogleMap, story: Story) {
  var route: Polyline = null
  var route2: Polyline = null

  def isEmpty = route == null || route.getPoints.size() == 0

  def init(): RouteManager = {
    if (story.locations.size() == 0) return this
    val routeOptions = new PolylineOptions()
    story.locations foreach { l ⇒
      routeOptions.add(l.asLatLng)
    }
    routeOptions.width(13)
    routeOptions.color(Color.WHITE)
    route = map.addPolyline(routeOptions)
    routeOptions.width(7)
    routeOptions.color(Color.parseColor("#1a6198"))
    route2 = map.addPolyline(routeOptions)
    this
  }

  def getBounds: LatLngBounds = {
    if (isEmpty) return null
    val boundsBuilder = LatLngBounds.builder()
    route.getPoints foreach { point ⇒
      boundsBuilder.include(point)
    }
    boundsBuilder.build()
  }

  def getPoints: java.util.List[LatLng] = {
    if (isEmpty) null else route.getPoints
  }

  def getStart: LatLng = {
    if (isEmpty) null else route.getPoints.get(0)
  }

  def getEnd: LatLng = {
    if (isEmpty) null else route.getPoints.get(route.getPoints.size() - 1)
  }

  def getStartBearing: java.lang.Float = {
    if (isEmpty) return null
    if (route.getPoints.size() == 1) return 0f
    getBearing(route.getPoints.get(0), route.getPoints.get(1))
  }

  def getBearing(p1: LatLng, p2: LatLng) = {
    val y = Math.sin(p2.longitude - p1.longitude) * Math.cos(p2.latitude)
    val x = Math.cos(p1.latitude) * Math.sin(p2.latitude) - Math.sin(p2.latitude) * Math.cos(p2.latitude) * Math.cos(p2.longitude - p1.longitude)
    (Math.atan2(y, x) * 180 / Math.PI).toFloat
  }

  def update() {
    if (route == null) {
      init()
      return
    }
    val points = story.locations.map(_.asLatLng)
    route.setPoints(points)
    route2.setPoints(points)
  }

  def remove() {
    if (route != null) route.remove()
    if (route2 != null) route2.remove()
  }
}
