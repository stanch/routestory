package net.routestory.parts

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Marker
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.TextView
import android.content.DialogInterface
import android.content.DialogInterface.OnDismissListener

object Implicits {
  implicit def func2OnCameraChangeListener(f: CameraPosition ⇒ Any) = new GoogleMap.OnCameraChangeListener() {
    def onCameraChange(p: CameraPosition) { f(p) }
  }

  implicit def func2OnMarkerClickListener(f: Marker ⇒ Boolean) = new GoogleMap.OnMarkerClickListener() {
    def onMarkerClick(m: Marker) = f(m)
  }

  implicit def thunk2OnDismissListener(f: ⇒ Any) = new DialogInterface.OnDismissListener {
    def onDismiss(d: DialogInterface) = f
  }

  implicit def thunk2OnClickListener(f: ⇒ Any) = new DialogInterface.OnClickListener {
    def onClick(d: DialogInterface, w: Int) = f
  }

  implicit def func2OnClickListener(f: (DialogInterface, Int) ⇒ Any) = new DialogInterface.OnClickListener {
    def onClick(d: DialogInterface, w: Int) = f(d, w)
  }

  implicit def thunk2OnCancelListener(f: ⇒ Any) = new DialogInterface.OnCancelListener() {
    def onCancel(d: DialogInterface) { f }
  }

  implicit def runnableSAM(f: ⇒ Any) = new Runnable {
    def run() = f
  }
}