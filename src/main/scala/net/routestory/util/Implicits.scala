package net.routestory.util

import android.content.DialogInterface
import android.location.Location
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.{ CameraPosition, LatLng ⇒ AndroidLL, Marker }
import com.javadocmd.simplelatlng.{ LatLng ⇒ SimpleLL }
import macroid.util.Ui

object Implicits {
  implicit def location2latlng(l: Location): AndroidLL = new AndroidLL(l.getLatitude, l.getLongitude)

  implicit def android2simple(l: SimpleLL): AndroidLL = new AndroidLL(l.getLatitude, l.getLongitude)

  implicit def simple2android(l: AndroidLL): SimpleLL = new SimpleLL(l.latitude, l.longitude)

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
