package net.routestory.display

import scala.concurrent.ExecutionContext
import scala.ref.WeakReference

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model._
import org.macroid.UiThreading._

import net.routestory.R
import net.routestory.model.Story._
import net.routestory.util.BitmapUtils
import net.routestory.util.Implicits._

class RouteMapManager(map: GoogleMap, displaySize: List[Int], activity: WeakReference[Activity])(maxImageSize: Int = displaySize.min / 4)(implicit ctx: Context)
  extends MapManager(map, displaySize, activity) {

  map.onMarkerClick(onMarkerClick)

  var man: Option[Marker] = None
  var markers: Map[Media, Marker] = Map.empty
  var markerDispatch: Map[Marker, Media] = Map.empty

  private def addMarker(location: LatLng, m: Media, icon: Either[Int, Bitmap]) = ui {
    markers += m → map.addMarker(new MarkerOptions()
      .position(location)
      .icon(icon.fold(BitmapDescriptorFactory.fromResource, BitmapDescriptorFactory.fromBitmap))
    )
    markerDispatch += markers(m) → m
  }

  def add(chapter: Chapter)(implicit ctx: Context, ec: ExecutionContext) = {
    super.addRoute(chapter)
    chapter.media foreach {
      case m: TextNote if !markers.contains(m) ⇒
        addMarker(m.location(chapter), m, Left(R.drawable.text_note))
      case m: VoiceNote if !markers.contains(m) ⇒
        addMarker(m.location(chapter), m, Left(R.drawable.voice_note))
      case m: Sound if !markers.contains(m) ⇒
        addMarker(m.location(chapter), m, Left(R.drawable.sound))
      case m: Heartbeat if !markers.contains(m) ⇒
        addMarker(m.location(chapter), m, Left(R.drawable.heart))
      case m: Venue if !markers.contains(m) ⇒
        addMarker(m.location(chapter), m, Left(R.drawable.foursquare))
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
    markers.values.foreach(_.remove())
    markers = Map.empty
    markerDispatch = Map.empty
  }

  lazy val onMarkerClick = { marker: Marker ⇒
    markerDispatch.get(marker).exists {
      case m: TextNote ⇒ onTextNoteClick(m)
      case m: Audio ⇒ onAudioClick(m)
      case m: Image ⇒ onImageClick(m)
      case m: Heartbeat ⇒ onHeartbeatClick(m)
      case m: Venue ⇒ onVenueClick(m)
      case _ ⇒ false
    }
  }
}
