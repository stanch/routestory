package net.routestory.browsing.story

import android.graphics.Bitmap
import android.util.{ Log, LruCache }
import com.google.android.gms.maps.model._
import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap }
import com.javadocmd.simplelatlng.LatLngTool
import io.dylemma.frp.EventSource
import macroid.FullDsl._
import macroid.Ui
import macroid.{ ActivityContext, AppContext }
import net.routestory.R
import net.routestory.data.{ Pruning, Clustering, Story }
import net.routestory.util.BitmapUtils
import net.routestory.util.Implicits._
import net.routestory.viewable.MarkerBitmaps

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MapManager(map: GoogleMap, iconAlpha: Float = 1.0f, centerIcons: Boolean = true)(implicit ctx: ActivityContext, appCtx: AppContext) {
  var route: Option[Polyline] = None
  var startMarker: Option[Marker] = None
  var manMarker: Option[Marker] = None

  val bitmapCache = new LruCache[Clustering.Tree[Marker], Future[Bitmap]](10)
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
          .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_action_directions))
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

  import BitmapDescriptorFactory.{ fromBitmap ⇒ iconBitmap, fromResource ⇒ iconResource }

  def addMarkers(chapter: Story.Chapter, tree: Option[Clustering.Tree[Unit]]) = Ui {
    markerTree = tree.map(_.map { x ⇒
      map.addMarker(new MarkerOptions()
        .position(x.location)
        .anchor(0.5f, 1f)
        .alpha(iconAlpha)
        .icon(iconResource(R.drawable.ic_action_place))
        .visible(false)
      )
    })
    markerTree.foreach(_.foreach { x ⇒
      markers += (x.data → x)
    })
    map.onMarkerClick(click(chapter))
    map.onCameraChange(cameraChange)
    cameraChange(map.getCameraPosition)
  }

  def markersAtScale(scale: Double) = Ui {
    markerTree foreach { t ⇒
      val newTrees = t.childrenAtScale(scale)
      (currentTrees diff newTrees).foreach { ct ⇒
        ct.data.setVisible(false)
        ct.data.setAlpha(0)
        ct.data.setIcon(iconResource(R.drawable.ic_action_place))
        ct.data.setAnchor(0.5f, 1f)
      }
      (newTrees diff currentTrees).foreach { ct ⇒
        // show a dummy marker only if there was nothing at all before
        if (currentTrees.isEmpty) ct.data.setAlpha(iconAlpha)
        ct.data.setVisible(true)
        MarkerBitmaps.bitmap(iconSize, bitmapCache)(ct)
          .map(BitmapUtils.cardFrame)
          .foreachUi { bitmap ⇒
            if (centerIcons) ct.data.setAnchor(0.5f, 0.5f)
            ct.data.setIcon(iconBitmap(bitmap))
            ct.data.setAlpha(iconAlpha)
          }
      }
      currentTrees = newTrees
    }
  }

  def removeMarkers() = Ui {
    markers.keys.foreach(_.remove())
    markers = Map.empty
    markerTree = None
    currentTrees = Vector.empty
    bitmapCache.evictAll()
    lastZoom = 0f
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
