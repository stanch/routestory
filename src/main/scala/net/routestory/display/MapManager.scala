package net.routestory.display

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

import android.app.{ AlertDialog, Dialog }
import android.content.{ Context, Intent }
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup.LayoutParams._
import android.widget.{ Button, FrameLayout, ImageView, TextView }

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.{ LatLngBounds, Polyline, PolylineOptions }
import org.macroid.{ AppContext, ActivityContext }
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import uk.co.senab.photoview.PhotoViewAttacher

import net.routestory.model.Story
import net.routestory.model.Story.Chapter
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._

class MapManager(map: GoogleMap, displaySize: List[Int])(implicit ctx: ActivityContext, appCtx: AppContext) {
  var route: Option[Polyline] = None

  def addRoute(chapter: Chapter) = route match {
    case Some(line) ⇒ line.setPoints(chapter.locations.map(_.coordinates))
    case None ⇒
      val routeOptions = new PolylineOptions
      chapter.locations.map(_.coordinates).foreach(routeOptions.add)
      routeOptions.width(7)
      routeOptions.color(Color.parseColor("#AD9A3D"))
      route = Some(map.addPolyline(routeOptions))
  }

  def removeRoute() {
    route.foreach(_.remove())
    route = None
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
    if (p.size < 2) 0f else p(0).bearingTo(p(1))
  }
}
