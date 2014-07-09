package net.routestory.maps

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model._
import macroid.UiThreading._
import macroid.util.Ui
import macroid.{ ActivityContext, AppContext }
import net.routestory.R
import net.routestory.data.Story.{ Chapter, KnownElement, UnknownElement }
import net.routestory.util.Implicits._

import scala.concurrent.ExecutionContext

class RouteMapManager(map: GoogleMap, displaySize: List[Int])(maxImageSize: Int = displaySize.min / 4)(implicit ctx: ActivityContext, appCtx: AppContext)
  extends MapManager(map, displaySize) {

  var man: Option[Marker] = None
  var markers: Map[KnownElement, Marker] = Map.empty
  var markerDispatch: Map[Marker, KnownElement] = Map.empty

  private def addMarker(data: KnownElement)(implicit markerable: Markerable[KnownElement]) = Ui {
    val marker = map.addMarker(new MarkerOptions().position(markerable.location(data)))
    markerable.icon(data, maxImageSize) foreachUi { bitmap ⇒
      marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
    }
    markers += data → marker
    markerDispatch += marker → data
  }

  def add(chapter: Chapter)(implicit ec: ExecutionContext) = {
    super.addRoute(chapter)

    // import typeclass instances
    val markerables = new Markerables(displaySize, chapter)
    import markerables._

    // add markers
    chapter.elements foreach {
      case m: UnknownElement ⇒ ()
      case m: KnownElement ⇒ if (!markers.contains(m)) addMarker(m).run
    }

    // update clicking
    map.onMarkerClick { marker ⇒
      markerDispatch.get(marker).exists(m ⇒ { implicitly[Markerable[KnownElement]].click(m).run; true })
    }
  }

  def updateMan(location: LatLng) {
    man.map(_.setPosition(location)).getOrElse {
      man = Some(map.addMarker(new MarkerOptions()
        .position(location)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.man))
      ))
    }
  }

  def remove() {
    super.removeRoute()
    man.foreach(_.remove())
    man = None
    markers.values.foreach(_.remove())
    markers = Map.empty
    markerDispatch = Map.empty
  }
}
