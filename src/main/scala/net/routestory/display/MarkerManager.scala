package net.routestory.display

import net.routestory.R
import net.routestory.parts.{ Styles, BitmapUtils }
import net.routestory.parts.BitmapUtils.MagicGrid
import android.app.{ Activity, Dialog, AlertDialog }
import android.content.{ Intent, Context, DialogInterface }
import android.content.DialogInterface.OnClickListener
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaPlayer
import android.view.{ Gravity, View, ViewGroup }
import android.widget._
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import net.routestory.parts.Implicits._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.macroid.{ MediaQueries, Tweaks, LayoutDsl, Concurrency }
import org.macroid.contrib.ExtraTweaks
import ViewGroup.LayoutParams._
import uk.co.senab.photoview.PhotoViewAttacher
import org.macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import org.macroid.contrib.ListAdapter
import android.net.Uri
import android.os.Vibrator
import net.routestory.model.Story.Chapter
import scala.ref.WeakReference
import net.routestory.model.Story

class MarkerManager(googleMap: GoogleMap, mapView: View, displaySize: List[Int], chapter: Chapter, activity: WeakReference[Activity])(implicit ctx: Context) extends Concurrency with MediaQueries {
  var hideOverlays = false

  val maxIconSize = ((800 dp) :: displaySize).min / 4

  abstract class MarkerItem(val timestamp: Int) {
    var marker: Option[Marker] = None
    val coords = chapter.locationAt(timestamp)
    var doi: Float = 0

    def createMarker() {
      marker = Some(googleMap.addMarker(new MarkerOptions()
        .position(coords)
        .icon(BitmapDescriptorFactory.fromBitmap(getIcon(scale = true)))
        .anchor(0.5f, 0.5f)))
    }

    // create a marker if not created and hide it
    def hideMarker() {
      marker.foreach(_.setVisible(false))
    }

    // create a marker if not created and show it
    def showMarker(dispatch: Map[Marker, MarkerItem]) = {
      marker.map(_.setIcon(BitmapDescriptorFactory.fromBitmap(getIcon(scale = true)))).getOrElse(createMarker())
      marker.foreach(_.setVisible(true))
      dispatch + (marker.get → this)
    }

    // add to list of markers to visualize
    def addToMarkerLists(shown: List[MarkerItem], hidden: List[MarkerItem], hide: Boolean = false): (List[MarkerItem], List[MarkerItem]) = {
      lazy val visible = googleMap.getProjection.getVisibleRegion.latLngBounds.contains(coords)
      if (hide || !visible) (shown, this :: hidden) else (this :: shown, hidden)
    }

    // add to list of things taking time to load
    def addToLoadingList(list: List[Future[Unit]]): List[Future[Unit]] = list

    // override these
    def getIcon(scale: Boolean): Bitmap
    def onClick() {}
  }

  // Image marker
  //  class ImageMarkerItem(data: Story.Image) extends MarkerItem(data.timestamp) with LayoutDsl with Tweaks with ExtraTweaks {
  //    private val icon = data.get(maxIconSize)
  //
  //    override def addToMarkerLists(shown: List[MarkerItem], hidden: List[MarkerItem], hide: Boolean = false): (List[MarkerItem], List[MarkerItem]) = {
  //      if (icon.value.get.isSuccess) super.addToMarkerLists(shown, hidden, hide) else (shown, this :: hidden)
  //    }
  //
  //    override def addToLoadingList(list: List[Future[Unit]]): List[Future[Unit]] = {
  //      icon.map(_ ⇒ ()) :: list
  //    }
  //
  //    override def getIcon(scale: Boolean): Bitmap = if (!scale) {
  //      icon.value.get.get
  //    } else {
  //      BitmapUtils.createScaledTransparentBitmap(icon.value.get.get, (maxIconSize * (0.95 + doi * 0.05)).toInt, 0.5 + doi * 0.5, true)
  //    }
  //
  //    override def onClick() {
  //      // show the image in a pop-up window
  //      //val progress = spinnerDialog("", "Loading image...") // TODO: strings.xml
  //      data.get(displaySize.max) foreachUi {
  //        case bitmap if bitmap != null ⇒
  //          //progress.dismiss()
  //          val view = w[ImageView] ~>
  //            lpOf[FrameLayout](MATCH_PARENT, MATCH_PARENT, Gravity.CENTER) ~>
  //            Image.bitmap(bitmap) ~> Image.adjustBounds ~>
  //            (_.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE))
  //          val attacher = new PhotoViewAttacher(view)
  //          attacher.update()
  //          new Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
  //            setContentView(l[FrameLayout](view))
  //            setOnDismissListener({ dialog: DialogInterface ⇒
  //              bitmap.recycle()
  //            })
  //            show()
  //          }
  //        case _ ⇒
  //        //progress.dismiss()
  //      }
  //    }
  //  }

  object IconMarkerItem {
    var iconPool = Map[Int, Bitmap]()
    def loadIcon(resourceId: Int): Bitmap = {
      if (!iconPool.contains(resourceId)) {
        iconPool += resourceId → BitmapFactory.decodeResource(ctx.getResources, resourceId)
      }
      iconPool(resourceId)
    }
  }
  abstract class IconMarkerItem(data: Story.Media, resourceId: Int) extends MarkerItem(data.timestamp) {
    private val icon = IconMarkerItem.loadIcon(resourceId)

    override def getIcon(scale: Boolean): Bitmap = icon /*if (!scale) {
      icon
    } else {
      BitmapUtils.createScaledTransparentBitmap(icon, (icon.getWidth, 1.0, false))
    }*/
  }

  // Audio marker
  class AudioMarkerItem(data: Story.Audio, resourceId: Int) extends IconMarkerItem(data, resourceId) {
    private var mediaPlayer: MediaPlayer = null

    //    override def onClick() {
    //      mediaPlayer = new MediaPlayer()
    //      data.get foreachUi {
    //        case file if file != null ⇒
    //          try {
    //            mediaPlayer.setDataSource(file.getAbsolutePath)
    //            mediaPlayer.prepare()
    //            mediaPlayer.start()
    //          } catch {
    //            case e: Throwable ⇒ e.printStackTrace()
    //          }
    //        case _ ⇒
    //      }
    //    }
  }

  class SoundMarkerItem(data: Story.Sound) extends AudioMarkerItem(data, R.drawable.sound)
  class VoiceMarkerItem(data: Story.VoiceNote) extends AudioMarkerItem(data, R.drawable.voice_note)

  // Text note marker
  class TextMarkerItem(data: Story.TextNote) extends IconMarkerItem(data, R.drawable.text_note) with ExtraTweaks {
    override def onClick() {
      val bld = new AlertDialog.Builder(ctx)
      val textView = w[TextView] ~>
        text(data.text) ~> TextSize.medium ~> Styles.p8dding ~>
        (_.setMaxWidth((displaySize(0) * 0.75).toInt))
      bld.setView(textView).create().show()
    }
  }

  // Foursquare venue marker
  class VenueMarkerItem(data: Story.Venue) extends IconMarkerItem(data, R.drawable.foursquare_bigger) with ExtraTweaks {
    override def onClick() {
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
    }
  }

  // Heartbeat marker
  class HeartbeatMarkerItem(data: Story.Heartbeat) extends IconMarkerItem(data, R.drawable.heart) {
    override def onClick() {
      ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator].vibrate(data.vibrationPattern(5), -1)
    }
  }

  object GroupMarkerItemFactory {
    import MarkerManager.distance
    def apply(item1: MarkerItem, item2: MarkerItem): GroupMarkerItem = {
      def mergeBounds(coords1: LatLng, coords2: LatLng) = LatLngBounds.builder.include(coords1).include(coords2).build()
      val (children: List[MarkerItem], closest: Double, bounds: LatLngBounds) = (item1, item2) match {
        // if one of the items is a grouping marker, and the other is not
        // the grouping one can absorb the non-grouping one
        // format: OFF
        case (group1: GroupMarkerItem, group2: GroupMarkerItem) ⇒ (
          List(group1, group2),
          distance(group1.coords, group2.coords),
          mergeBounds(group1.coords, group2.coords)
        )
        case (group: GroupMarkerItem, single) if group.children.forall(item ⇒ distance(item.coords, single.coords) <= 2*group.closest) ⇒
          (single :: group.children, group.closest, group.bounds)
        case (single, group: GroupMarkerItem) if group.children.forall(item ⇒ distance(item.coords, single.coords) <= 2*group.closest) ⇒
          (single :: group.children, group.closest, group.bounds)
        case (single1, single2) ⇒ (
          List(single1, single2),
          distance(single1.coords, single2.coords),
          mergeBounds(single1.coords, single2.coords)
        )
        // format: ON
      }
      new GroupMarkerItem(children, closest, bounds)
    }
  }

  // Grouping marker
  class GroupMarkerItem(val children: List[MarkerItem], val closest: Double, val bounds: LatLngBounds)
    extends MarkerItem((children map { _.timestamp } sum) / children.length) with LayoutDsl with ExtraTweaks {
    lazy val leafList: List[MarkerItem] = children flatMap {
      case g: GroupMarkerItem ⇒ g.leafList
      case i ⇒ i :: Nil
    }

    lazy val icon = {
      // group and count markers of each type
      //val Image = classOf[ImageMarkerItem]
      val bitmaps = leafList.groupBy(_.getClass).toList.flatMap {
        //case (Image, items) ⇒ items.map(_.getIcon(scale = false))
        case (c, head :: items) ⇒ if (items.length > 0) {
          BitmapUtils.createCountedBitmap(head.getIcon(scale = false), items.length + 1) :: Nil
        } else {
          head.getIcon(scale = false) :: Nil
        }
        case (_, Nil) ⇒ Nil // make compiler happy
      }
      MagicGrid.createSquarishGrid(bitmaps, maxIconSize)
    }
    lazy val iconSize = Math.min(Math.max(icon.getWidth, icon.getHeight), maxIconSize)

    override def addToMarkerLists(shown: List[MarkerItem], hidden: List[MarkerItem], hide: Boolean = false): (List[MarkerItem], List[MarkerItem]) = {
      val fits = seemsToFit()
      // hiding self if hide=true or if children fit
      val self = super.addToMarkerLists(shown, hidden, hide || fits)
      // hiding children if hide=true or if they don’t fit
      children.foldLeft(self) { case ((s, h), c) ⇒ c.addToMarkerLists(s, h, hide || !fits) }
    }

    override def addToLoadingList(list: List[Future[Unit]]): List[Future[Unit]] = {
      children.foldLeft(list)((l, c) ⇒ c.addToLoadingList(l))
    }

    private var wasExpanded = false
    private def seemsToFit() = {
      // check if the closest pair of children is not overlapping
      // now features a hysteresis
      val List(ne, sw) = List(bounds.northeast, bounds.southwest).map(googleMap.getProjection.toScreenLocation)
      wasExpanded = MarkerManager.manhattanDistance(ne, sw) > maxIconSize + (if (wasExpanded) -5.dp else 5.dp)
      wasExpanded
    }

    override def getIcon(scale: Boolean): Bitmap = {
      BitmapUtils.createScaledTransparentBitmap(icon, (iconSize * (0.95 + doi * 0.05)).toInt, 0.5 + doi * 0.5, true)
    }

    override def onClick() {
      val List(ne, sw) = List(bounds.northeast, bounds.southwest) map { googleMap.getProjection.toScreenLocation }
      // check if there is a zoom level at which we can expand
      if (MarkerManager.manhattanDistance(ne, sw) * Math.pow(2, googleMap.getMaxZoomLevel - 2 - googleMap.getCameraPosition.zoom) < maxIconSize) {
        // show a confusion resolving dialog

        new AlertDialog.Builder(ctx)
          .setAdapter(ListAdapter.simple(leafList)(
            w[ImageView] ~> Image.adjustBounds,
            item ⇒ Image.bitmap {
              val icon = item.getIcon(scale = false)
              BitmapUtils.createScaledBitmap(icon, Math.min(Math.max(icon.getWidth, icon.getHeight), maxIconSize))
            }
          ), new OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int) {
              leafList(which).onClick()
            }
          })
          .create().show()
      } else {
        // expand
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, (maxIconSize / 1.5).toInt))
      }
    }
  }

  var markerDispatch = Map[Marker, MarkerItem]()

  lazy val rootMarkerItem: Option[MarkerItem] = {
    val markerItems: Vector[MarkerItem] = chapter.media.toVector.flatMap {
      case m: Story.Sound ⇒ new SoundMarkerItem(m) +: Vector.empty
      case m: Story.VoiceNote ⇒ new VoiceMarkerItem(m) +: Vector.empty
      case m: Story.TextNote ⇒ new TextMarkerItem(m) +: Vector.empty
      case m: Story.Heartbeat ⇒ new HeartbeatMarkerItem(m) +: Vector.empty
      case m: Story.Venue ⇒ new VenueMarkerItem(m) +: Vector.empty
      case _ ⇒ Vector.empty
    }
    markerItems.length match {
      case 0 ⇒ None
      case 1 ⇒ Some(markerItems(0))
      case _ ⇒ Some(clusterRounds(markerItems.sortBy(_.timestamp), chapter.duration.toDouble / 4))
    }
  }

  lazy val loadingItems = rootMarkerItem.map(_.addToLoadingList(List())) getOrElse List[Future[Unit]]()
  private lazy val readyFuture = Future.sequence(loadingItems)

  // naïve agglomerative clustering with an heuristic to group markers that are close in time
  private def clusterRounds(_markerItems: Vector[MarkerItem], _clusterRadius: Double): MarkerItem = {
    var distanceTable = Map[(MarkerItem, MarkerItem), Double]()
    var markerItems = _markerItems
    var clusterRadius = _clusterRadius / 2

    while (markerItems.length > 1) {
      // refill the distance table
      while (distanceTable.isEmpty) {
        clusterRadius *= 2
        for (current +: next ← markerItems.tails) {
          distanceTable ++= (next takeWhile {
            _.timestamp < current.timestamp + clusterRadius
          } filterNot { neighbor ⇒
            distanceTable.contains((current, neighbor))
          } map { neighbor ⇒
            (current, neighbor) → MarkerManager.distance(current.coords, neighbor.coords)
          } toMap)
        }
      }

      // search for the closest clusters
      val closest = distanceTable.par.minBy(_._2)._1

      // remove them
      markerItems = markerItems diff List(closest._1, closest._2)
      distanceTable = distanceTable filter {
        case ((item1, item2), _) ⇒
          (Set(item1, item2) & Set(closest._1, closest._2)).isEmpty
      }

      // merge them
      val group = GroupMarkerItemFactory(closest._1, closest._2)
      val index = markerItems lastIndexWhere { _.timestamp <= group.timestamp }
      val (left, right) = markerItems.splitAt(index + 1)
      markerItems = left ++ Vector(group) ++ right

      // update distance table
      // format: OFF
      distanceTable ++=
        ((right takeWhile { _.timestamp < group.timestamp + clusterRadius }) map { item ⇒
          (group, item) → MarkerManager.distance(group.coords, item.coords)
        } toMap) ++
        ((left dropWhile { _.timestamp < group.timestamp - clusterRadius }) map { item ⇒
          (item, group) → MarkerManager.distance(item.coords, group.coords)
        } toMap)
      // format: ON
    }
    markerItems(0)
  }

  private def doiEvaluate(markerItems: List[MarkerItem]) {
    // if there’s just one marker, simply assign doi=1
    if (markerItems.length == 1) {
      markerItems.head.doi = 1
      return
    }

    // check if there is an image marker in the small centered “focus” area
    // if so, select it as the center
    val (width, height) = (mapView.getWidth, mapView.getHeight)
    val p = googleMap.getProjection
    val radius = Math.min(width, height) / 5
    val _center = new Point(width / 2, height / 2)
    val center = markerItems map { item ⇒
      (item, MarkerManager.chebyshevDistance(p.toScreenLocation(item.coords), _center))
    } filter {
      //case ((_: ImageMarkerItem | _: GroupMarkerItem), d) if d < radius ⇒ true
      case _ ⇒ false
    } match {
      case l if l.length == 0 ⇒ _center
      case l ⇒ p.toScreenLocation(l.minBy(_._2)._1.coords)
    }

    // assign degrees of interest
    val n = (width + height) / 2.toFloat
    markerItems foreach { item ⇒
      // Manhattan distance is used for optimization, and also due to the fact that
      // the images are rectangular and therefore produce less confusion when moved apart diagonally
      val d = MarkerManager.manhattanDistance(p.toScreenLocation(item.coords), center) / n
      item.doi = 1 - 1.5f * d
    }
  }

  def update() {
    if (!readyFuture.isCompleted) return
    if (rootMarkerItem.isEmpty) return
    if (hideOverlays) {
      val (_, hidden) = rootMarkerItem.get.addToMarkerLists(Nil, Nil, hide = true)
      hidden.foreach(_.hideMarker())
      return
    }

    // produce a list of markers to show and hide
    val (shown, hidden) = rootMarkerItem.get.addToMarkerLists(Nil, Nil)

    // calculate degrees if interest
    doiEvaluate(shown)

    // hide hidden markers
    hidden.foreach(_.hideMarker())

    // show visible markers
    val temp = markerDispatch
    markerDispatch = shown.foldLeft(temp)((d, i) ⇒ i.showMarker(d))
  }

  def remove() {
    markerDispatch foreach { case (m, i) ⇒ m.remove() }
  }

  def onMarkerClick(marker: Marker): Boolean = markerDispatch.get(marker).exists { x ⇒ x.onClick(); true }
}

object MarkerManager {
  def distance(pt1: LatLng, pt2: LatLng): Double = {
    Math.sqrt(Math.pow(pt2.latitude - pt1.latitude, 2) + Math.pow(pt2.longitude - pt1.longitude, 2))
  }

  def manhattanDistance(pt1: Point, pt2: Point): Int = {
    Math.abs(pt2.x - pt1.x) + Math.abs(pt2.y - pt1.y)
  }

  def chebyshevDistance(pt1: Point, pt2: Point): Int = {
    Math.max(Math.abs(pt2.x - pt1.x), Math.abs(pt2.y - pt1.y))
  }
}
