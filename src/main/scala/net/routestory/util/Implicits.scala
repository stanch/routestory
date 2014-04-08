package net.routestory.util

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.{ LatLng, CameraPosition, Marker }
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.TextView
import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener
import android.location.Location
import macroid.util.Ui

object Implicits {
  implicit class RichLatLng(l: LatLng) {
    def bearingTo(other: LatLng) = {
      val (lat1, lat2, dlng) = (l.latitude.toRadians, other.latitude.toRadians, (other.longitude - l.longitude).toRadians)
      val y = Math.sin(dlng) * Math.cos(lat2)
      val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dlng)
      Math.atan2(y, x).toDegrees.toFloat
    }
  }

  implicit def location2latlng(l: Location): LatLng = new LatLng(l.getLatitude, l.getLongitude)

  implicit class RichMap(map: GoogleMap) {
    def onCameraChange(f: CameraPosition ⇒ Any) = map.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener {
      def onCameraChange(p: CameraPosition) { f(p) }
    })

    def onMarkerClick(f: Marker ⇒ Boolean) = map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener {
      def onMarkerClick(m: Marker) = f(m)
    })
  }

  implicit def thunk2OnDismissListener(f: ⇒ Any) = new DialogInterface.OnDismissListener {
    def onDismiss(d: DialogInterface) = f
  }

  implicit def thunk2OnCancelListener(f: ⇒ Any) = new DialogInterface.OnCancelListener {
    def onCancel(d: DialogInterface) { f }
  }

  implicit def runnableUi(f: Ui[Any]) = new Runnable {
    def run() = f.get
  }
}