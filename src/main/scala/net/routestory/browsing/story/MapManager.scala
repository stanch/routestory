package net.routestory.browsing.story

import android.graphics.BitmapFactory
import com.google.android.gms.maps.model._
import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap }
import com.javadocmd.simplelatlng.LatLngTool
import io.dylemma.frp.EventSource
import macroid.FullDsl._
import macroid.Ui
import macroid.{ ActivityContext, AppContext }
import net.routestory.R
import net.routestory.data.{ Clustering, Story }
import net.routestory.util.BitmapUtils
import net.routestory.util.Implicits._
import net.routestory.viewable.MarkerBitmaps

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global

class MapManager(map: GoogleMap, iconAlpha: Float = 1.0f, centerIcons: Boolean = true)(implicit ctx: ActivityContext, appCtx: AppContext) {
  var route: Option[Polyline] = None
  var startMarker: Option[Marker] = None
  var manMarker: Option[Marker] = None

  var markerTree: Option[Clustering.Tree[Marker]] = None
  var currentTrees: Vector[Clustering.Tree[Marker]] = Vector.empty
  var markers: Map[Marker, Clustering.Tree[Marker]] = Map.empty

  val iconSize = 100.dp

  def addRoute(chapter: Story.Chapter) = Ui {
    route match {
      case Some(line) ⇒ line.setPoints(chapter.locations.map(_.data: LatLng))
      case None ⇒
        val routeOptions = new PolylineOptions
        chapter.locations.map(_.data: LatLng).foreach(routeOptions.add)
        routeOptions.width(10)
        routeOptions.color(appCtx.get.getResources.getColor(R.color.orange))
        route = Some(map.addPolyline(routeOptions))
    }
    startMarker match {
      case Some(s) ⇒ s.setPosition(start.get)
      case None ⇒
        startMarker = Some(map.addMarker(new MarkerOptions()
          .position(start.get).anchor(0.5f, 0.5f)
          .flat(true).rotation(startBearing.get.toFloat - 90f)
          .icon(BitmapDescriptorFactory.fromBitmap {
            BitmapUtils.cardFrame(BitmapFactory.decodeResource(appCtx.get.getResources, R.drawable.ic_action_directions))
          })
        ))
    }
  }

  def removeRoute() = Ui {
    route.foreach(_.remove())
    route = None
    startMarker.foreach(_.remove())
    startMarker = None
  }

  def addMan(location: LatLng) = Ui {
    manMarker match {
      case Some(m) ⇒ m.setPosition(location)
      case None ⇒
        manMarker = Some(map.addMarker(new MarkerOptions()
          .position(location).anchor(0.5f, 1f)
          .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
        ))
    }
  }

  def removeMan() = Ui {
    manMarker.foreach(_.remove())
    manMarker = None
  }

  def addMarkers(chapter: Story.Chapter, tree: Option[Clustering.Tree[Unit]]) = Ui {
    lastZoom = 0f
    val bitmapTree = tree.map(MarkerBitmaps.withBitmaps(iconSize))
    markerTree = bitmapTree.map(_.map { x ⇒
      val marker = map.addMarker(new MarkerOptions().position(x.location)
        .anchor(0.5f, 1f).alpha(iconAlpha)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_action_place)).visible(false)
      )
      x.data.map(BitmapUtils.cardFrame) foreachUi { b ⇒
        if (centerIcons) marker.setAnchor(0.5f, 0.5f)
        marker.setIcon(BitmapDescriptorFactory.fromBitmap(b))
      }
      marker
    })
    markerTree.foreach(_.foreach { x ⇒
      markers += (x.data → x)
    })
    map.onMarkerClick(click(chapter))
    map.onCameraChange(cameraChange)
    cameraChange(map.getCameraPosition)
  }

  def markersAtScale(scale: Double) = Ui {
    markers.keys.foreach(_.setVisible(false))
    markerTree foreach { t ⇒
      currentTrees = t.childrenAtScale(scale)
      currentTrees.foreach(_.data.setVisible(true))
    }
  }

  def removeMarkers() = Ui {
    markers.keys.foreach(_.remove())
    markers = Map.empty
    markerTree = None
    currentTrees = Vector.empty
  }

  def remove() = removeRoute() ~ removeMarkers()

  def focus(focus: Int) = Ui {
    Clustering.indexLookup(currentTrees, focus) foreach { t ⇒
      map.moveCamera(CameraUpdateFactory.newLatLng(currentTrees(t).location))
    }
  }

  val cueStream = EventSource[Int]()
  val scaleStream = EventSource[Double]()
  var lastZoom = 0f

  def click(chapter: Story.Chapter)(marker: Marker) = {
    markers.get(marker).foreach {
      case x @ Clustering.Leaf(_, _, _, _) ⇒
        ElementPager.show(x, cueStream.fire).run
      case x @ Clustering.Node(_, _, _, _) ⇒
        ElementChooser.show(x, cueStream.fire).run
    }
    true
  }

  def cameraChange(pos: CameraPosition) = {
    if (pos.zoom != lastZoom) {
      val worldWidth = 256 * Math.pow(2.0, pos.zoom.toDouble)
      val radianWidth = worldWidth / Math.PI
      val scale = iconSize / radianWidth
      scaleStream.fire(scale)
      markersAtScale(scale).run
      lastZoom = pos.zoom
    }
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
