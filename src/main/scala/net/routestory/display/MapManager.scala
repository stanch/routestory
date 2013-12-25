package net.routestory.display

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.ref.WeakReference
import scala.util.Try

import android.app.{ Activity, AlertDialog, Dialog }
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
import org.macroid.UiThreading._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import uk.co.senab.photoview.PhotoViewAttacher

import net.routestory.model.Story
import net.routestory.model.Story.Chapter
import net.routestory.ui.Styles._
import net.routestory.util.Implicits._

class MapManager(map: GoogleMap, displaySize: List[Int], activity: WeakReference[Activity])(implicit ctx: Context) {
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

  def onImageClick(data: Story.Image) = {
    data.fetchAndLoad(displaySize.max) foreachUi { bitmap ⇒
      val view = w[ImageView] ~> Image.bitmap(bitmap) ~> Image.adjustBounds ~> lowProfile
      new PhotoViewAttacher(view).update()
      new Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
        setContentView(l[FrameLayout](view ~> lp(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER)))
        setOnDismissListener(bitmap.recycle())
        show()
      }
    }
    true
  }

  private var mediaPlayer: Option[MediaPlayer] = None
  def onAudioClick(data: Story.Audio) = {
    mediaPlayer = Some(new MediaPlayer)
    data.fetch foreachUi { file ⇒
      Try {
        mediaPlayer.foreach { p ⇒
          p.setDataSource(file.getAbsolutePath)
          p.prepare()
          p.start()
        }
      }
    }
    true
  }

  def onTextNoteClick(data: Story.TextNote) = {
    val bld = new AlertDialog.Builder(ctx)
    val textView = w[TextView] ~> text(data.text) ~>
      TextSize.medium ~> p8dding ~>
      (_.setMaxWidth((displaySize(0) * 0.75).toInt))
    bld.setView(textView).create().show()
    true
  }

  def onHeartbeatClick(data: Story.Heartbeat) = {
    ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator].vibrate(data.vibrationPattern(5), -1)
    true
  }

  def onVenueClick(data: Story.Venue) = {
    val bld = new AlertDialog.Builder(ctx)
    val view = l[VerticalLinearLayout](
      w[TextView] ~> text(data.name) ~> TextSize.large ~> padding(left = 3 sp),
      w[Button] ~> text("Open in Foursquare®") ~> On.click {
        val intent = new Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(s"https://foursquare.com/v/${data.id}"))
        activity().startActivityForResult(intent, 0)
      }
    )
    bld.setView(view).create().show()
    true
  }
}
