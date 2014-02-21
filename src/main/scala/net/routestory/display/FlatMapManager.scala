package net.routestory.display

import scala.collection.JavaConversions._
import scala.concurrent.{ Future, future }
import scala.concurrent.ExecutionContext.Implicits.global

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.graphics.{ Bitmap, Point }
import android.view.View
import android.widget._

import com.google.android.gms.maps.{ CameraUpdateFactory, GoogleMap }
import com.google.android.gms.maps.model._
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._

import net.routestory.model.Story
import net.routestory.util.BitmapUtils
import net.routestory.util.BitmapUtils.MagicGrid
import net.routestory.util.Implicits._
import net.routestory.model.Story.Chapter
import org.macroid.{ AppContext, ActivityContext }
import org.macroid.viewable.{ FillableViewable, FillableViewableAdapter }

class FlatMapManager(map: GoogleMap, mapView: View, displaySize: List[Int])(implicit ctx: ActivityContext, appCtx: AppContext)
  extends MapManager(map, displaySize) {

  map.onMarkerClick(onMarkerClick)
  var hideOverlays = false
  val maxIconSize = ((800 dp) :: displaySize).min / 4

  sealed trait MarkerItem {
    var marker: Option[Marker] = None
    var doi: Float = 0

    // hide the marker
    def hideMarker() {
      marker.foreach(_.setVisible(false))
    }

    def createMarker = {
      marker = Some(map.addMarker(new MarkerOptions().position(location).anchor(0.5f, 0.5f)))
      marker.get
    }

    // create the marker if not created and show it
    def showMarker(dispatch: Map[Marker, MarkerItem]) = {
      val m = marker.getOrElse(createMarker)
      icon(scale = true).foreachUi(i ⇒ m.setIcon(BitmapDescriptorFactory.fromBitmap(i)))
      marker.foreach(_.setVisible(true))
      dispatch + (m → this)
    }

    // add to list of markers to visualize
    def addToMarkerLists(shown: List[MarkerItem], hidden: List[MarkerItem], hide: Boolean = false): (List[MarkerItem], List[MarkerItem]) = {
      lazy val visible = map.getProjection.getVisibleRegion.latLngBounds.contains(location)
      if (hide || !visible) (shown, this :: hidden) else (this :: shown, hidden)
    }

    // implement these
    def timestamp: Int
    def location: LatLng
    def icon(scale: Boolean): Future[Bitmap]
    def iconType: Option[Int]
    def click(): Unit
  }

  class SingleMarkerItem[A](data: A, val timestamp: Int)(implicit markerable: Markerable[A]) extends MarkerItem {
    val location = markerable.location(data)
    val _icon = markerable.icon(data, maxIconSize)
    val iconType = markerable.iconType(data)
    def icon(scale: Boolean) = _icon
    def click() = markerable.click(data)
  }

  class ImageMarkerItem[A](data: A, val timestamp: Int)(implicit markerable: Markerable[A]) extends MarkerItem {
    val location = markerable.location(data)
    val _icon = markerable.icon(data, maxIconSize)
    val iconType = markerable.iconType(data)
    def icon(scale: Boolean) = if (!scale) {
      _icon
    } else _icon map { i ⇒
      BitmapUtils.createScaledTransparentBitmap(i, (maxIconSize * (0.95 + doi * 0.05)).toInt, 0.5 + doi * 0.5, border = true)
    }
    def click() = markerable.click(data)
  }

  object GroupMarkerItem {
    import FlatMapManager._
    def apply(item1: MarkerItem, item2: MarkerItem): GroupMarkerItem = {
      def mergeBounds(coords1: LatLng, coords2: LatLng) = LatLngBounds.builder.include(coords1).include(coords2).build()
      val (children: Vector[MarkerItem], closest: Double, bounds: LatLngBounds) = (item1, item2) match {
        // if one of the items is a grouping marker, and the other is not
        // the grouping one can absorb the non-grouping one
        // format: OFF
        case (group1: GroupMarkerItem, group2: GroupMarkerItem) ⇒ (
          Vector(group1, group2),
          distance(group1.location, group2.location),
          mergeBounds(group1.location, group2.location)
        )
        case (group: GroupMarkerItem, single) if group.children.forall(item ⇒ distance(item.location, single.location) <= 2*group.closest) ⇒
          (single +: group.children, group.closest, group.bounds.including(single.location))
        case (single, group: GroupMarkerItem) if group.children.forall(item ⇒ distance(item.location, single.location) <= 2*group.closest) ⇒
          (single +: group.children, group.closest, group.bounds.including(single.location))
        case (single1, single2) ⇒ (
          Vector(single1, single2),
          distance(single1.location, single2.location),
          mergeBounds(single1.location, single2.location)
        )
        // format: ON
      }
      new GroupMarkerItem(children, closest, bounds)
    }
  }

  // Grouping marker
  class GroupMarkerItem(val children: Vector[MarkerItem], val closest: Double, val bounds: LatLngBounds)
    extends MarkerItem {

    val location = bounds.getCenter
    val timestamp = children.map(_.timestamp).sum / children.length

    lazy val leaves: Vector[MarkerItem] = children flatMap {
      case g: GroupMarkerItem ⇒ g.leaves
      case i ⇒ Vector(i)
    }

    val iconType = None
    lazy val _icon = {
      // group and count markers of each type
      val bitmaps = leaves.groupBy(_.iconType).toVector.flatMap {
        case (None, items) ⇒
          items.map(_.icon(scale = false))
        case (_, items @ Vector(i, j, _*)) ⇒
          items.take(1).map(_.icon(scale = false).map(BitmapUtils.createCountedBitmap(_, items.length)))
        case (_, items @ Vector(i)) ⇒
          items.take(1).map(_.icon(scale = false))
      }
      Future.sequence(bitmaps).map(MagicGrid.create(_, maxIconSize))
    }
    lazy val iconSize = _icon.map(i ⇒ Math.min(Math.max(i.getWidth, i.getHeight), maxIconSize))

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

    def icon(scale: Boolean) = (_icon zip iconSize) map {
      case (i, s) ⇒
        BitmapUtils.createScaledTransparentBitmap(i, (s * (0.95 + doi * 0.05)).toInt, 0.5 + doi * 0.5, border = true)
    }

    def click() = {
      val List(ne, sw) = List(bounds.northeast, bounds.southwest) map { map.getProjection.toScreenLocation }
      // check if there is a zoom level at which we can expand
      if (FlatMapManager.manhattanDistance(ne, sw) * Math.pow(2, map.getMaxZoomLevel - 2 - map.getCameraPosition.zoom) < maxIconSize) {
        Future.sequence(leaves.map(_.icon(scale = false))) foreachUi { icons ⇒
          //show a confusion resolving dialog
          new AlertDialog.Builder(ctx.get)
            .setAdapter(FillableViewableAdapter(icons)(FillableViewable.tw(
              w[ImageView] ~> Image.adjustBounds,
              icon ⇒ Image.bitmap {
                BitmapUtils.createScaledBitmap(icon, Math.min(Math.max(icon.getWidth, icon.getHeight), maxIconSize))
              }
            )), new OnClickListener() {
              def onClick(dialog: DialogInterface, which: Int) {
                leaves(which).click()
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

    // import typeclass instances
    val markerables = new Markerables(displaySize, chapter)
    import markerables._

    // create marker items
    val markerItems: Vector[MarkerItem] = chapter.media.toVector flatMap {
      case m: Story.UnknownMedia ⇒ Vector.empty
      case m: Story.Image ⇒ new ImageMarkerItem(m, m.timestamp) +: Vector.empty
      case m: Story.KnownMedia ⇒ new SingleMarkerItem(m, m.timestamp) +: Vector.empty
    }

    // cluster
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
            (current, neighbor) → FlatMapManager.distance(current.location, neighbor.location)
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
      val group = GroupMarkerItem(closest._1, closest._2)
      val index = markerItems lastIndexWhere { _.timestamp <= group.timestamp }
      val (left, right) = markerItems.splitAt(index + 1)
      markerItems = left ++ Vector(group) ++ right

      // update distance table
      // format: OFF
      distanceTable ++=
        ((right takeWhile { _.timestamp < group.timestamp + clusterRadius }) map { item ⇒
          (group, item) → FlatMapManager.distance(group.location, item.location)
        } toMap) ++
        ((left dropWhile { _.timestamp < group.timestamp - clusterRadius }) map { item ⇒
          (item, group) → FlatMapManager.distance(item.location, group.location)
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
      (item, FlatMapManager.chebyshevDistance(p.toScreenLocation(item.location), _center))
    } filter {
      case ((_: ImageMarkerItem[_] | _: GroupMarkerItem), d) if d < radius ⇒ true
      case _ ⇒ false
    } match {
      case l if l.length == 0 ⇒ _center
      case l ⇒ p.toScreenLocation(l.minBy(_._2)._1.location)
    }

    // assign degrees of interest
    val n = (width + height) / 2.toFloat
    markerItems foreach { item ⇒
      // Manhattan distance is used for optimization, and also due to the fact that
      // the images are rectangular and therefore produce less confusion when moved apart diagonally
      val d = FlatMapManager.manhattanDistance(p.toScreenLocation(item.location), center) / n
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

  lazy val onMarkerClick = { marker: Marker ⇒ markerDispatch.get(marker).exists { x ⇒ x.click(); true } }
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
