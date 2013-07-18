package net.routestory.display

import java.io.File
import net.routestory.R
import net.routestory.model.Story
import net.routestory.parts.{ CacherSupport, BitmapUtils }
import net.routestory.parts.BitmapUtils.MagicGrid
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaPlayer
import android.os.Handler
import android.os.Vibrator
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.Projection
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import net.routestory.parts.Implicits._
import scala.collection.JavaConversions._
import scala.collection.SeqExtractors
import org.scaloid.common._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.macroid.Concurrency

class MarkerManager(googleMap: GoogleMap, displaySize: List[Int], story: Story)(implicit ctx: Context) extends Concurrency with CacherSupport {
    var hide_overlays = false

    val maxIconSize = ((800 dip) :: displaySize).min / 4

    abstract class MarkerItem(val timestamp: Int) {
        val coords = story.getLocation(timestamp)
        var doi: Float = 0

        // add to list of markers to visualize
        def addToMarkerList(list: List[MarkerItem]): List[MarkerItem] = {
            //this :: list
            if (googleMap.getProjection.getVisibleRegion.latLngBounds.contains(coords)) this :: list else list
        }

        // add to list of things taking time to load
        def addToLoadingList(list: List[Future[Unit]]): List[Future[Unit]] = list

        // override these
        def getIcon(scale: Boolean): Bitmap
        def onClick() {}
    }

    // Image marker
    class ImageMarkerItem(data: Story.ImageData) extends MarkerItem(data.timestamp) {
        private val icon = cacher2Future(data.get(maxIconSize))

        override def addToMarkerList(list: List[MarkerItem]): List[MarkerItem] = {
            if (icon.value.get.isSuccess) super.addToMarkerList(list) else list
        }

        override def addToLoadingList(list: List[Future[Unit]]): List[Future[Unit]] = {
            icon.map(_ ⇒ ()) :: list
        }

        override def getIcon(scale: Boolean): Bitmap = if (!scale) {
            icon.value.get.get
        } else {
            BitmapUtils.createScaledTransparentBitmap(icon.value.get.get, (maxIconSize * (0.8 + doi * 0.2)).toInt, 0.3 + doi * 0.7, true)
        }

        override def onClick() {
            // show the image in a pop-up window
            val bld = new AlertDialog.Builder(ctx)
            val imageView = new ImageView(ctx)
            val progress = spinnerDialog("", "Loading image...") // TODO: strings.xml
            data.get(displaySize.max) onSuccessUi {
                case bitmap if bitmap != null ⇒
                    progress.dismiss()
                    bld.setView(imageView).setOnDismissListener({ dialog: DialogInterface ⇒
                        bitmap.recycle()
                    }).create().show()
                    imageView.setImageBitmap(bitmap)
                case _ ⇒
                    progress.dismiss()
            }
        }
    }

    object IconMarkerItem {
        var iconPool = Map[Int, Bitmap]()
        def loadIcon(resourceId: Int): Bitmap = {
            if (!iconPool.contains(resourceId)) {
                iconPool += resourceId → BitmapFactory.decodeResource(ctx.getResources, resourceId)
            }
            iconPool(resourceId)
        }
    }
    abstract class IconMarkerItem(data: Story.TimedData, resourceId: Int) extends MarkerItem(data.timestamp) {
        private val icon = IconMarkerItem.loadIcon(resourceId)

        override def getIcon(scale: Boolean): Bitmap = if (!scale) {
            icon
        } else {
            BitmapUtils.createScaledTransparentBitmap(icon, (icon.getWidth() * (0.9 + doi * 0.1)).toInt, 0.6 + doi * 0.4, false)
        }
    }

    // Audio marker
    class AudioMarkerItem(data: Story.AudioData, resourceId: Int) extends IconMarkerItem(data, resourceId) {
        private var mediaPlayer: MediaPlayer = null

        override def onClick() {
            mediaPlayer = new MediaPlayer();
            data.get() onSuccessUi {
                case file if file != null ⇒
                    try {
                        mediaPlayer.setDataSource(file.getAbsolutePath())
                        mediaPlayer.prepare()
                        mediaPlayer.start()
                    } catch {
                        case e: Throwable ⇒ e.printStackTrace()
                    }
            }
        }
    }

    class SoundMarkerItem(data: Story.AudioData) extends AudioMarkerItem(data, R.drawable.sound)
    class VoiceMarkerItem(data: Story.AudioData) extends AudioMarkerItem(data, R.drawable.mic)

    // Text note marker
    class TextMarkerItem(data: Story.TextData) extends IconMarkerItem(data, R.drawable.note) {
        override def onClick() {
            val bld = new AlertDialog.Builder(ctx)
            val textView = new TextView(ctx)
            textView.setMaxWidth((displaySize(0) * 0.75).toInt)
            textView.setText(data.text)
            textView.setTextAppearance(ctx, android.R.style.TextAppearance_Medium)
            bld.setView(textView).create().show()
        }
    }

    // Heartbeat marker
    class HeartbeatMarkerItem(data: Story.HeartbeatData) extends IconMarkerItem(data, R.drawable.heart) {
        override def onClick() {
            vibrator.vibrate(data.getVibrationPattern(5), -1)
        }
    }

    object GroupMarkerItemFactory {
        def apply(item1: MarkerItem, item2: MarkerItem): GroupMarkerItem = {
            val (children: List[MarkerItem], closest: Double, bounds: LatLngBounds) = {
                // see if one of the items is a grouping marker, and the other is not
                // and the grouping one can absorb the non-grouping one
                val List(group, other) = List(item1, item2).sortBy(!_.isInstanceOf[GroupMarkerItem])
                if (group.isInstanceOf[GroupMarkerItem] && !other.isInstanceOf[GroupMarkerItem] && (
                    group.asInstanceOf[GroupMarkerItem].children forall { item ⇒
                        MarkerManager.distance(item.coords, other.coords) <= 2 * group.asInstanceOf[GroupMarkerItem].closest
                    })) {
                    (
                        other :: group.asInstanceOf[GroupMarkerItem].children,
                        group.asInstanceOf[GroupMarkerItem].closest,
                        group.asInstanceOf[GroupMarkerItem].bounds)
                } else {
                    (
                        List(group, other),
                        MarkerManager.distance(group.coords, other.coords), {
                            val boundsBuilder = LatLngBounds.builder()
                            boundsBuilder.include(group.coords)
                            boundsBuilder.include(other.coords)
                            boundsBuilder.build()
                        })
                }
            }
            new GroupMarkerItem(children, closest, bounds)
        }
    }

    // Grouping marker
    class GroupMarkerItem(val children: List[MarkerItem], val closest: Double, val bounds: LatLngBounds)
        extends MarkerItem((children map { _.timestamp } sum) / children.length) {
        lazy val leafList: List[MarkerItem] = children flatMap {
            case g: GroupMarkerItem ⇒ g.leafList
            case i ⇒ i :: Nil
        }

        lazy val icon = {
            // group and count markers of each type
            val Image = classOf[ImageMarkerItem]
            val bitmaps = (leafList.groupBy(_.getClass).flatMap {
                case (Image, items) ⇒ items.map(_.getIcon(scale = false))
                case (c, head :: items) ⇒ if (items.length > 0) {
                    BitmapUtils.createCountedBitmap(head.getIcon(scale = false), items.length + 1) :: Nil
                } else {
                    head.getIcon(scale = false) :: Nil
                }
                case (_, Nil) ⇒ Nil // make compiler happy
            }).toList
            MagicGrid.createSquarishGrid(bitmaps, maxIconSize)
        }
        lazy val iconSize = Math.min(Math.max(icon.getWidth, icon.getHeight), maxIconSize)

        override def addToMarkerList(list: List[MarkerItem]): List[MarkerItem] = {
            if (!seemsToFit) super.addToMarkerList(list) else children.foldLeft(list)((l, c) ⇒ c.addToMarkerList(l))
        }

        override def addToLoadingList(list: List[Future[Unit]]): List[Future[Unit]] = {
            children.foldLeft(list)((l, c) ⇒ c.addToLoadingList(l))
        }

        private var wasExpanded = false
        private def seemsToFit = {
            // check if the closest pair of children is not overlapping
            // now features a hysteresis
            val List(ne, sw) = List(bounds.northeast, bounds.southwest).map(googleMap.getProjection.toScreenLocation)
            wasExpanded = MarkerManager.manhattanDistance(ne, sw) > maxIconSize + (if (wasExpanded) -5 dip else 5 dip)
            wasExpanded
        }

        override def getIcon(scale: Boolean): Bitmap = {
            BitmapUtils.createScaledTransparentBitmap(icon, (iconSize * (0.8 + doi * 0.2)).toInt, 0.3 + doi * 0.7, true)
        }

        override def onClick() {
            val p = googleMap.getProjection
            val List(ne, sw) = List(bounds.northeast, bounds.southwest) map { googleMap.getProjection.toScreenLocation }
            // check if there is a zoom level at which we can expand
            if (MarkerManager.manhattanDistance(ne, sw) * Math.pow(2, googleMap.getMaxZoomLevel - googleMap.getCameraPosition.zoom) < maxIconSize) {
                // show a confusion resolving dialog
                new AlertDialog.Builder(ctx)
                    .setAdapter(new ArrayAdapter[MarkerItem](ctx, 0, 0, leafList) {
                        override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
                            val view = if (itemView == null) {
                                val v = new LinearLayout(ctx)
                                val imageView = new ImageView(ctx)
                                imageView.setAdjustViewBounds(true)
                                imageView.setId(1)
                                v.asInstanceOf[LinearLayout].addView(imageView)
                                v
                            } else itemView
                            val icon = leafList(position).getIcon(scale = false)
                            view.findViewById(1).asInstanceOf[ImageView].setImageBitmap(
                                BitmapUtils.createScaledBitmap(icon, Math.min(Math.max(icon.getWidth, icon.getHeight), maxIconSize)))
                            view
                        }
                    }, new OnClickListener() {
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
        val markerItems = Vector(
            story.photos, story.audio, story.voice,
            story.notes, story.heartbeat) zip Vector(
                classOf[ImageMarkerItem], classOf[SoundMarkerItem], classOf[VoiceMarkerItem],
                classOf[TextMarkerItem], classOf[HeartbeatMarkerItem]) flatMap {
                    case (d, m) ⇒
                        if (d != null) d map { v ⇒ m.getConstructor(classOf[MarkerManager], v.getClass).newInstance(this, v) } else Nil
                } sortBy {
                    _.timestamp
                }

        markerItems.length match {
            case 0 ⇒ None
            case 1 ⇒ Some(markerItems(0))
            case _ ⇒ Some(clusterRounds(markerItems, story.duration.toDouble / 4))
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
            val closest = (distanceTable.par.minBy(_._2))._1

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
            distanceTable ++=
                ((right takeWhile { _.timestamp < group.timestamp + clusterRadius }) /*.par*/ map { item ⇒
                    (group, item) → MarkerManager.distance(group.coords, item.coords)
                } toMap) ++
                ((left dropWhile { _.timestamp < group.timestamp - clusterRadius }) /*.par*/ map { item ⇒
                    (item, group) → MarkerManager.distance(item.coords, group.coords)
                } toMap)
        }
        markerItems(0)
    }

    private def doiEvaluate(markerItems: List[MarkerItem]) {
        // check if there is an image marker in the small centered "focus" area
        // if so, select it as the center
        val List(width, height) = displaySize
        val p = googleMap.getProjection
        val radius = Math.min(width, height) / 8
        val _center = new Point(width / 2, height / 2)
        val center = markerItems.par map { item ⇒
            (item, MarkerManager.chebyshevDistance(p.toScreenLocation(item.coords), _center))
        } filter {
            case (i: ImageMarkerItem, d) if d < radius ⇒ true
            case _ ⇒ false
        } match {
            case l if l.length == 0 ⇒ _center
            case l ⇒ p.toScreenLocation((l.minBy(_._2))._1.coords)
        }

        // assign degrees of interest
        val n = (width + height) / 2.toFloat
        markerItems.par foreach { item ⇒
            // Manhattan distance is used for optimization, and also due to the fact that
            // the images are rectangular and therefore produce less confusion when moved apart diagonally
            val d = MarkerManager.manhattanDistance(p.toScreenLocation(item.coords), center) / n
            item.doi = (1 - 1.5f * d)
        }
    }

    def update() {
        if (!readyFuture.isCompleted) return
        if (hide_overlays) {
            remove()
            return
        }

        // produce a list of markers to show
        val markerItems = rootMarkerItem.get.addToMarkerList(List())

        // calculate degrees if interest
        doiEvaluate(markerItems)

        // show the markers
        val icons = markerItems.par.map(_.getIcon(scale = true))
        val temp = (markerItems.zip(icons) map {
            case (item, icon) ⇒
                googleMap.addMarker(new MarkerOptions()
                    .position(item.coords)
                    .icon(BitmapDescriptorFactory.fromBitmap(icon))
                    .anchor(0.5f, 0.5f)) → item
        }).toMap
        remove()
        markerDispatch = temp
    }

    def remove() {
        markerDispatch foreach { case (m, i) ⇒ m.remove() }
    }

    def onMarkerClick(marker: Marker): Boolean = markerDispatch.get(marker) match {
        case Some(m) ⇒ m.onClick(); true
        case None ⇒ false
    }
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
