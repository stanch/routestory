package net.routestory.display

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model._
import org.macroid.UiThreading._

import net.routestory.R
import net.routestory.model.Story._
import net.routestory.util.Implicits._
import org.macroid.{ AppContext, ActivityContext }

class RouteMapManager(map: GoogleMap, displaySize: List[Int])(maxImageSize: Int = displaySize.min / 4)(implicit ctx: ActivityContext, appCtx: AppContext)
  extends MapManager(map, displaySize) {

  var man: Option[Marker] = None
  var markers: Map[KnownMedia, Marker] = Map.empty
  var markerDispatch: Map[Marker, KnownMedia] = Map.empty

  private def addMarker(data: KnownMedia)(implicit markerable: Markerable[KnownMedia]) = ui {
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
    chapter.media foreach {
      case m: UnknownMedia ⇒ ()
      case m: KnownMedia if !markers.contains(m) ⇒ addMarker(m)
    }

    // update clicking
    map.onMarkerClick { marker ⇒
      markerDispatch.get(marker).exists(m ⇒ { implicitly[Markerable[KnownMedia]].click(m); true })
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
