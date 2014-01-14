package net.routestory.display

import scala.collection.JavaConversions._
import scala.concurrent.{ Future, future }
import scala.concurrent.ExecutionContext.Implicits.global

import android.app.{ Activity, AlertDialog }
import android.content.{ Context, DialogInterface }
import android.content.DialogInterface.OnClickListener
import android.graphics.{ Bitmap, BitmapFactory, Point }
import android.view.View
import android.widget._

import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model._
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.ListAdapter

import net.routestory.R
import net.routestory.model.Story
import net.routestory.model.Story.Chapter
import net.routestory.util.BitmapUtils
import net.routestory.util.BitmapUtils.MagicGrid
import net.routestory.util.Implicits._
import scala.Some
import net.routestory.model.Story.Chapter
import org.macroid.{ AppContext, ActivityContext }

class FlatMapManager(map: GoogleMap, mapView: View, displaySize: List[Int])(implicit ctx: ActivityContext, appCtx: AppContext)
  extends MapManager(map, displaySize) {

  map.onMarkerClick(onMarkerClick)
  var hideOverlays = false
  val maxIconSize = ((800 dp) :: displaySize).min / 4

  abstract class MarkerItem(chapter: Chapter, val timestamp: Int) {
    var marker: Option[Marker] = None
    val coords = chapter.locationAt(timestamp)
    var doi: Float = 0

    // hide the marker
    def hideMarker() {
      marker.foreach(_.setVisible(false))
    }

    def createMarker = {
      marker = Some(map.addMarker(new MarkerOptions().position(coords).anchor(0.5f, 0.5f)))
      marker.get
    }

    // create the marker if not created and show it
    def showMarker(dispatch: Map[Marker, MarkerItem]) = {
      val m = marker.getOrElse(createMarker)
      getIcon(scale = true).foreachUi(icon ⇒ m.setIcon(BitmapDescriptorFactory.fromBitmap(icon)))
      marker.foreach(_.setVisible(true))
      dispatch + (m → this)
    }

    // add to list of markers to visualize
    def addToMarkerLists(shown: List[MarkerItem], hidden: List[MarkerItem], hide: Boolean = false): (List[MarkerItem], List[MarkerItem]) = {
      lazy val visible = map.getProjection.getVisibleRegion.latLngBounds.contains(coords)
      if (hide || !visible) (shown, this :: hidden) else (this :: shown, hidden)
    }

    // override these
    def getIcon(scale: Boolean): Future[Bitmap]
    def onClick() {}
  }

  // Image marker
  class ImageMarkerItem(chapter: Chapter, data: Story.Image)
    extends MarkerItem(chapter, data.timestamp) {

    private val icon = data.fetchAndLoad(maxIconSize)

    override def getIcon(scale: Boolean) = if (!scale) {
      icon
    } else icon.map { i ⇒
      BitmapUtils.createScaledTransparentBitmap(i, (maxIconSize * (0.95 + doi * 0.05)).toInt, 0.5 + doi * 0.5, border = true)
    }

    override def onClick() {
      onImageClick(data)
    }
  }

  object IconMarkerItem {
    var iconPool = Map[Int, Bitmap]()
    def loadIcon(resourceId: Int): Bitmap = {
      if (!iconPool.contains(resourceId)) {
        iconPool += resourceId → BitmapFactory.decodeResource(appCtx.get.getResources, resourceId)
      }
      iconPool(resourceId)
    }
  }
  abstract class IconMarkerItem(chapter: Chapter, data: Story.Media, resourceId: Int)
    extends MarkerItem(chapter, data.timestamp) {

    private lazy val icon = IconMarkerItem.loadIcon(resourceId)
    override def getIcon(scale: Boolean) = Future.successful(icon)
  }

  // Audio marker
  class AudioMarkerItem(chapter: Chapter, data: Story.Audio, resourceId: Int)
    extends IconMarkerItem(chapter, data, resourceId) {

    override def onClick() {
      onAudioClick(data)
    }
  }

  class SoundMarkerItem(chapter: Chapter, data: Story.Sound) extends AudioMarkerItem(chapter, data, R.drawable.sound)
  class VoiceMarkerItem(chapter: Chapter, data: Story.VoiceNote) extends AudioMarkerItem(chapter, data, R.drawable.voice_note)

  // Text note marker
  class TextMarkerItem(chapter: Chapter, data: Story.TextNote)
    extends IconMarkerItem(chapter, data, R.drawable.text_note) {

    override def onClick() {
      onTextNoteClick(data)
    }
  }

  // Foursquare venue marker
  class VenueMarkerItem(chapter: Chapter, data: Story.Venue)
    extends IconMarkerItem(chapter, data, R.drawable.foursquare_bigger) {

    override def onClick() {
      onVenueClick(data)
    }
  }

  // Heartbeat marker
  class HeartbeatMarkerItem(chapter: Chapter, data: Story.Heartbeat)
    extends IconMarkerItem(chapter, data, R.drawable.heart) {

    override def onClick() {
      onHeartbeatClick(data)
    }
  }

  object GroupMarkerItem {
    import FlatMapManager.distance
    def apply(chapter: Chapter, item1: MarkerItem, item2: MarkerItem): GroupMarkerItem = {
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
      new GroupMarkerItem(chapter, children, closest, bounds)
    }
  }

  // Grouping marker
  class GroupMarkerItem(chapter: Chapter, val children: List[MarkerItem], val closest: Double, val bounds: LatLngBounds)
    extends MarkerItem(chapter, children.map(_.timestamp).sum / children.length) {
    lazy val leafList: List[MarkerItem] = children flatMap {
      case g: GroupMarkerItem ⇒ g.leafList
      case i ⇒ i :: Nil
    }

    lazy val icon = {
      // group and count markers of each type
      val Image = classOf[ImageMarkerItem]
      val bitmaps = leafList.groupBy(_.getClass).toList.flatMap {
        case (Image, items) ⇒ items.map(_.getIcon(scale = false))
        case (c, head :: items) ⇒ if (items.length > 0) {
          head.getIcon(scale = false).map(BitmapUtils.createCountedBitmap(_, items.length + 1)) :: Nil
        } else {
          head.getIcon(scale = false) :: Nil
        }
        case (_, Nil) ⇒ Nil // make compiler happy
      }
      Future.sequence(bitmaps).map(MagicGrid.create(_, maxIconSize))
    }
    lazy val iconSize = icon.map(i ⇒ Math.min(Math.max(i.getWidth, i.getHeight), maxIconSize))

    override def addToMarkerLists(shown: List[MarkerItem], hidden: List[MarkerItem], hide: Boolean = false): (List[MarkerItem], List[MarkerItem]) = {
      val fits = seemsToFit()
      // hiding self if hide=true or if children fit
      val self = super.addToMarkerLists(shown, hidden, hide || fits)
      // hiding children if hide=true or if they don’t fit
      children.foldLeft(self) { case ((s, h), c) ⇒ c.addToMarkerLists(s, h, hide || !fits) }
    }

    private var wasExpanded = false
    private def seemsToFit() = {
      // check if the closest pair of children is not overlapping
      // now features a hysteresis
      val List(ne, sw) = List(bounds.northeast, bounds.southwest).map(map.getProjection.toScreenLocation)
      wasExpanded = FlatMapManager.manhattanDistance(ne, sw) > maxIconSize + (if (wasExpanded) -5.dp else 5.dp)
      wasExpanded
    }

    override def getIcon(scale: Boolean) = (icon zip iconSize) map {
      case (i, s) ⇒
        BitmapUtils.createScaledTransparentBitmap(i, (s * (0.95 + doi * 0.05)).toInt, 0.5 + doi * 0.5, true)
    }

    override def onClick() {
      val List(ne, sw) = List(bounds.northeast, bounds.southwest) map { map.getProjection.toScreenLocation }
      // check if there is a zoom level at which we can expand
      if (FlatMapManager.manhattanDistance(ne, sw) * Math.pow(2, map.getMaxZoomLevel - 2 - map.getCameraPosition.zoom) < maxIconSize) {
        Future.sequence(leafList.map(_.getIcon(scale = false))) foreachUi { icons ⇒
          //show a confusion resolving dialog
          new AlertDialog.Builder(ctx.get)
            .setAdapter(ListAdapter.simple(icons)(
              w[ImageView] ~> Image.adjustBounds,
              icon ⇒ Image.bitmap {
                BitmapUtils.createScaledBitmap(icon, Math.min(Math.max(icon.getWidth, icon.getHeight), maxIconSize))
              }
            ), new OnClickListener() {
              def onClick(dialog: DialogInterface, which: Int) {
                leafList(which).onClick()
              }
            }).create().show()
        }
      } else {
        // expand
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, (maxIconSize / 1.5).toInt))
      }
    }
  }

  var markerDispatch = Map[Marker, MarkerItem]()
  var rootMarkerItem: Option[MarkerItem] = None

  def add(chapter: Chapter) {
    super.addRoute(chapter)
    val markerItems: Vector[MarkerItem] = chapter.media.toVector.flatMap {
      case m: Story.Image ⇒ new ImageMarkerItem(chapter, m) +: Vector.empty
      case m: Story.Sound ⇒ new SoundMarkerItem(chapter, m) +: Vector.empty
      case m: Story.VoiceNote ⇒ new VoiceMarkerItem(chapter, m) +: Vector.empty
      case m: Story.TextNote ⇒ new TextMarkerItem(chapter, m) +: Vector.empty
      case m: Story.Heartbeat ⇒ new HeartbeatMarkerItem(chapter, m) +: Vector.empty
      case m: Story.Venue ⇒ new VenueMarkerItem(chapter, m) +: Vector.empty
      case _ ⇒ Vector.empty
    }
    future {
      rootMarkerItem = markerItems.length match {
        case 0 ⇒ None
        case 1 ⇒ Some(markerItems(0))
        case _ ⇒ Some(clusterRounds(chapter, markerItems.sortBy(_.timestamp), chapter.duration.toDouble / 4))
      }
    }
  }

  // naïve agglomerative clustering with an heuristic to group markers that are close in time
  private def clusterRounds(chapter: Chapter, _markerItems: Vector[MarkerItem], _clusterRadius: Double): MarkerItem = {
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
            (current, neighbor) → FlatMapManager.distance(current.coords, neighbor.coords)
          } toMap)
        }
      }

      // search for the closest clusters
      val closest = distanceTable.minBy(_._2)._1

      // remove them
      markerItems = markerItems diff List(closest._1, closest._2)
      distanceTable = distanceTable filter {
        case ((item1, item2), _) ⇒
          (Set(item1, item2) & Set(closest._1, closest._2)).isEmpty
      }

      // merge them
      val group = GroupMarkerItem(chapter, closest._1, closest._2)
      val index = markerItems lastIndexWhere { _.timestamp <= group.timestamp }
      val (left, right) = markerItems.splitAt(index + 1)
      markerItems = left ++ Vector(group) ++ right

      // update distance table
      // format: OFF
      distanceTable ++=
        ((right takeWhile { _.timestamp < group.timestamp + clusterRadius }) map { item ⇒
          (group, item) → FlatMapManager.distance(group.coords, item.coords)
        } toMap) ++
        ((left dropWhile { _.timestamp < group.timestamp - clusterRadius }) map { item ⇒
          (item, group) → FlatMapManager.distance(item.coords, group.coords)
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
    val p = map.getProjection
    val radius = Math.min(width, height) / 5
    val _center = new Point(width / 2, height / 2)
    val center = markerItems map { item ⇒
      (item, FlatMapManager.chebyshevDistance(p.toScreenLocation(item.coords), _center))
    } filter {
      case ((_: ImageMarkerItem | _: GroupMarkerItem), d) if d < radius ⇒ true
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
      val d = FlatMapManager.manhattanDistance(p.toScreenLocation(item.coords), center) / n
      item.doi = 1 - 1.5f * d
    }
  }

  def update() {
    // short-circuiting
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
    markerDispatch = shown.foldLeft(markerDispatch)((d, i) ⇒ i.showMarker(d))
  }

  def remove() {
    super.removeRoute()
    markerDispatch.keys.foreach(_.remove())
  }

  lazy val onMarkerClick = { marker: Marker ⇒ markerDispatch.get(marker).exists { x ⇒ x.onClick(); true } }
}

object FlatMapManager {
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
