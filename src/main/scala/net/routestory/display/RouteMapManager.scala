package net.routestory.display

import android.graphics.Bitmap
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model._
import net.routestory.model.Story._
import net.routestory.R
import net.routestory.parts.BitmapUtils
import org.macroid.UiThreading._
import android.content.Context
import scala.concurrent.ExecutionContext

class RouteMapManager(map: GoogleMap, maxImageSize: Int) extends MapManager(map) {
  var markers: Map[Media, Marker] = Map.empty

  private def addMarker(location: LatLng, m: Media, icon: Either[Int, Bitmap]) = ui {
    markers += m → map.addMarker(new MarkerOptions()
      .position(location)
      .icon(icon.fold(BitmapDescriptorFactory.fromResource, BitmapDescriptorFactory.fromBitmap))
    )
  }

  def add(chapter: Chapter)(implicit ctx: Context, ec: ExecutionContext) = {
    super.addRoute(chapter)
    chapter.media foreach {
      case m: TextNote if !markers.contains(m) ⇒
        addMarker(m.location(chapter), m, Left(R.drawable.text_note))
      case m: Heartbeat if !markers.contains(m) ⇒
        addMarker(m.location(chapter), m, Left(R.drawable.heart))
      case m: Photo if !markers.contains(m) ⇒
        m.fetchAndLoad(maxImageSize) onSuccess {
          case bitmap if bitmap != null ⇒
            val scaled = BitmapUtils.createScaledTransparentBitmap(bitmap, Math.min(Math.max(bitmap.getWidth, bitmap.getHeight), maxImageSize), 0.8, false)
            addMarker(m.location(chapter), m, Right(scaled))
          case _ ⇒
        }
      case _ ⇒
    }
  }

  def remove() {
    super.removeRoute()
    markers.values.foreach(_.remove())
    markers = Map.empty
  }
}
