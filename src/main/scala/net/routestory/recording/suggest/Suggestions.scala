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
import org.macroid.contrib.ListAdapter
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import org.needs.{ flickr, foursquare }

import net.routestory.{ R, Apis }
import net.routestory.recording.Cartographer
import net.routestory.ui.RouteStoryFragment
import net.routestory.ui.Styles._
import net.routestory.util.FragmentData
import org.macroid.AppContext
import org.macroid.ActivityContext
import android.os.Bundle

trait ListViewable[A] {
  type Slots
  def makeView(implicit ctx: ActivityContext): (View, Slots)
  def fillView(s: Slots, a: A)(implicit ctx: ActivityContext): Any
}

object Suggestables {
  implicit object foursquareListViewable extends ListViewable[foursquare.Venue] {
    class Slots {
      var name = slot[TextView]
    }
    def makeView(implicit ctx: ActivityContext) = {
      val slots = new Slots
      val layout = l[HorizontalLinearLayout](
        w[ImageView] ~> org.macroid.Tweak[ImageView](_.setImageResource(R.drawable.foursquare)),
        w[TextView] ~> wire(slots.name)
      )
      (layout, slots)
    }
    def fillView(s: Slots, a: foursquare.Venue)(implicit ctx: ActivityContext) = s.name ~> text(a.name)
  }

  implicit object flickrListViewable extends ListViewable[flickr.Photo] {
    class Slots {
      var title = slot[TextView]
      var owner = slot[TextView]
    }
    def makeView(implicit ctx: ActivityContext) = {
      val slots = new Slots
      val layout = l[VerticalLinearLayout](
        w[TextView] ~> wire(slots.title),
        w[TextView] ~> wire(slots.owner)
      )
      (layout, slots)
    }
    def fillView(s: Slots, a: flickr.Photo)(implicit ctx: ActivityContext) = {
      s.title ~> text(a.title)
      s.owner ~> text(s"by ${a.owner.name}")
    }
  }
}

class SuggestionsFragment extends RouteStoryFragment with FragmentData[ActorSystem] {
  import Suggestables._
  import SuggestionsFragment._

  lazy val typewriter = getFragmentData.actorSelection("/user/typewriter")
  lazy val cartographer = getFragmentData.actorSelection("/user/cartographer")
  lazy val suggester = getFragmentData.actorSelection("/user/suggester")

  lazy val foursquareAdapter = Adapter[foursquare.Venue](typewriter)
  lazy val flickrAdapter = Adapter[flickr.Photo](typewriter)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    l[ScrollView](
      l[VerticalLinearLayout](
        w[TextView] ~> text("Foursquare") ~> TextSize.large ~> padding(bottom = 8 dp),
        w[ListView] ~> adaptr(foursquareAdapter),
        w[TextView] ~> text("Flickr") ~> TextSize.large ~> padding(top = 12 dp, bottom = 8 dp),
        w[ListView] ~> adaptr(flickrAdapter)
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
  case class Adapter[A](typewriter: ActorSelection)(implicit ctx: ActivityContext, appCtx: AppContext, listViewable: ListViewable[A]) extends ListAdapter[A, View] {
    def makeView = {
      val (layout, slots) = listViewable.makeView
      layout ~> hold(slots)
    }
    def fillView(view: View, parent: ViewGroup, data: A) = {
      val slots = view.getTag.asInstanceOf[listViewable.Slots]
      listViewable.fillView(slots, data)
      view ~> On.click { typewriter ! data }
    }
  }
}

object Suggester {
  case class AttachUi(fragment: SuggestionsFragment)
  case object DetachUi
  case object Ping
  case class Venues(venues: List[foursquare.Venue])
  case class Photos(photos: List[flickr.Photo])
  def props(apis: Apis) = Props(new Suggester(apis))
}

class Suggester(apis: Apis) extends Actor {
  import Suggester._

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

    case Venues(venues) ⇒ ui {
      attachedUi foreach { f ⇒
        f.foursquareAdapter.clear()
        f.foursquareAdapter.addAll(venues.asJava)
      }
    }

    case Photos(photos) ⇒ ui {
      attachedUi foreach { f ⇒
        f.flickrAdapter.clear()
        f.flickrAdapter.addAll(photos.asJava)
      }
    }

    case _ ⇒
  }
}
