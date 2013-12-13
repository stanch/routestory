package net.routestory.parts

import scala.concurrent.{ ExecutionContext, Promise, Future, future }
import android.location.{ LocationManager, LocationListener, Location ⇒ Loc }
import android.os.Bundle
import android.content.Context
import java.util.Locale

object Location extends org.macroid.UiThreading {
  def getBbox(loc: Loc) = {
    // see [http://janmatuschek.de/LatitudeLongitudeBoundingCoordinates]
    val conv = Math.PI / 180
    val lat = loc.getLatitude * conv
    val lng = loc.getLongitude * conv
    val r = 0.5 // 500 meters
    val latr = r / 6371.0 // Earth radius
    val lngr = Math.asin(Math.sin(r) / Math.cos(lat)) // TODO: fix poles and 180 meridian
    // use Locale.US to render floating-point numbers with points
    "%f,%f,%f,%f".formatLocal(Locale.US, (lat - latr) / conv, (lng - lngr) / conv, (lat + latr) / conv, (lng + lngr) / conv)
  }

  def getLocation(implicit ctx: Context, ec: ExecutionContext): Future[Option[Loc]] = {
    val locationPromise = Promise[Option[Loc]]()
    val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

    val locationListener = new LocationListener {
      def onLocationChanged(location: Loc) {
        locationPromise.trySuccess(Some(location))
        locationManager.removeUpdates(this)
      }
      override def onStatusChanged(provider: String, status: Int, extras: Bundle) {}
      override def onProviderEnabled(provider: String) {}
      override def onProviderDisabled(provider: String) {}
    }
    ui {
      locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, locationListener)
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, locationListener)
    }
    future {
      Thread.sleep(3000)
      locationPromise.trySuccess(None)
    }
    locationPromise.future
  }
}
