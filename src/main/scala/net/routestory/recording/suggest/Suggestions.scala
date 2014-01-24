package net.routestory.recording.suggest

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget._

import akka.actor._
import akka.pattern.pipe
import com.google.android.gms.maps.model.LatLng
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import org.macroid.viewable.{ FillableViewable, FillableViewableAdapter, SlottedFillableViewable }
import org.macroid.viewable.Viewable._
import org.needs.{ flickr, foursquare }

import net.routestory.{ R, Apis }
import net.routestory.recording.Cartographer
import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._
import net.routestory.util.FragmentData
import org.macroid.{ AppContext, ActivityContext }
import android.os.Bundle

object Suggestables {
  implicit object foursquareListViewable extends SlottedFillableViewable[foursquare.Venue] {
    class Slots {
      var name = slot[TextView]
    }
    def makeSlots(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val layout = l[HorizontalLinearLayout](
        w[ImageView] ~> org.macroid.Tweak[ImageView](_.setImageResource(R.drawable.foursquare)),
        w[TextView] ~> wire(slots.name)
      )
      (layout, slots)
    }
    def fillSlots(slots: Slots, data: foursquare.Venue)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      slots.name ~> text(data.name)
    }
  }

  implicit object flickrListViewable extends SlottedFillableViewable[flickr.Photo] {
    class Slots {
      var title = slot[TextView]
      var owner = slot[TextView]
    }
    def makeSlots(implicit ctx: ActivityContext, appCtx: AppContext) = {
      val slots = new Slots
      val layout = l[VerticalLinearLayout](
        w[TextView] ~> wire(slots.title),
        w[TextView] ~> wire(slots.owner)
      )
      (layout, slots)
    }
    def fillSlots(slots: Slots, data: flickr.Photo)(implicit ctx: ActivityContext, appCtx: AppContext) = {
      slots.title ~> text(data.title)
      slots.owner ~> text(s"by ${data.owner.name}")
    }
  }
}

class SuggestionsFragment extends RouteStoryFragment with FragmentData[ActorSystem] {
  import Suggestables._
  import SuggestionsFragment._

  lazy val typewriter = getFragmentData.actorSelection("/user/typewriter")
  lazy val cartographer = getFragmentData.actorSelection("/user/cartographer")
  lazy val suggester = getFragmentData.actorSelection("/user/suggester")

  var venues = slot[LinearLayout]
  var photos = slot[LinearLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    l[ScrollView](
      l[VerticalLinearLayout](
        w[TextView] ~> text("Foursquare") ~> TextSize.large ~> padding(bottom = 8 dp),
        w[VerticalLinearLayout] ~> wire(venues),
        w[TextView] ~> text("Flickr") ~> TextSize.large ~> padding(top = 12 dp, bottom = 8 dp),
        w[VerticalLinearLayout] ~> wire(photos)
      )
    )
  }

  override def onStart() {
    super.onStart()
    suggester ! Suggester.AttachUi(this)
  }

  override def onStop() {
    super.onStop()
    suggester ! Suggester.DetachUi
  }
}

object SuggestionsFragment {
  case class Adapter[A](typewriter: ActorSelection)(implicit ctx: ActivityContext, appCtx: AppContext, fillableViewable: FillableViewable[A])
    extends FillableViewableAdapter[A] {

    override def getView(position: Int, view: View, parent: ViewGroup) =
      super.getView(position, view, parent) ~> On.click { typewriter ! getItem(position) }
  }
}

object Suggester {
  case class AttachUi(fragment: SuggestionsFragment)
  case object DetachUi
  case object Ping
  case class Venues(venues: List[foursquare.Venue])
  case class Photos(photos: List[flickr.Photo])
  def props(apis: Apis)(implicit ctx: ActivityContext, appCtx: AppContext) = Props(new Suggester(apis))
}

class Suggester(apis: Apis)(implicit ctx: ActivityContext, appCtx: AppContext) extends Actor with ActorLogging {
  import Suggester._
  import Suggestables._

  var attachedUi: Option[SuggestionsFragment] = None
  var pings: Option[Cancellable] = None

  lazy val cartographer = context.actorSelection("../cartographer")

  def receive = {
    case AttachUi(fragment) ⇒
      attachedUi = Some(fragment)
      pings = Some(context.system.scheduler.schedule(5 seconds, 5 seconds, self, Ping))

    case DetachUi ⇒
      attachedUi = None
      pings.foreach(_.cancel())

    case Ping ⇒
      cartographer ! Cartographer.QueryLastLocation

    case Some(l: LatLng) ⇒
      apis.foursquareApi.NeedNearbyVenues(l.latitude, l.longitude, 100).go.map(Venues) pipeTo self
      apis.flickrApi.NeedNearbyPhotos(l.latitude, l.longitude, 10).go.map(Photos) pipeTo self

    case Venues(venues) ⇒ attachedUi foreach { f ⇒
      ui {
        f.venues ~> addViews(venues.map(_.layout), removeOld = true)
      }
    }

    case Photos(photos) ⇒ attachedUi foreach { f ⇒
      ui {
        f.photos ~> addViews(photos.map(_.layout), removeOld = true)
      }
    }

    case _ ⇒
  }
}
