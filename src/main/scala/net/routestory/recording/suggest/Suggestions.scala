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
import org.needs.{ flickr, foursquare }

import net.routestory.{ RouteStoryApp, R, Apis }
import net.routestory.recording.Cartographer
import net.routestory.ui.{ CollapsedListView, RouteStoryFragment }
import net.routestory.ui.Styles._
import android.os.Bundle
import org.macroid.{ Tweak, AppContext, ActivityContext }
import net.routestory.display.Suggestables
import org.macroid.akkafragments.{ AkkaFragment, FragmentActor }

class SuggestionsFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/suggester"))
  lazy val typewriter = actorSystem.actorSelection("/user/typewriter")

  lazy val suggestables = Suggestables(app, typewriter)
  import suggestables._

  var venues = slot[CollapsedListView[foursquare.Venue]]
  var photos = slot[CollapsedListView[flickr.Photo]]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    l[ScrollView](
      l[VerticalLinearLayout](
        new CollapsedListView[foursquare.Venue](
          w[TextView] ~> TextSize.large ~> p8dding ~> text("Foursquare")
        ) ~> wire(venues),
        new CollapsedListView[flickr.Photo](
          w[TextView] ~> TextSize.large ~> p8dding ~> text("Flickr")
        ) ~> wire(photos)
      )
    )
  }
}

object Suggester {
  case object Ping
  case class Venues(venues: List[foursquare.Venue])
  case class Photos(photos: List[flickr.Photo])
  def props(apis: Apis)(implicit ctx: ActivityContext, appCtx: AppContext) = Props(new Suggester(apis))
}

class Suggester(apis: Apis)(implicit ctx: ActivityContext, appCtx: AppContext) extends FragmentActor[SuggestionsFragment] with ActorLogging {
  import Suggester._
  import FragmentActor._

  var pings: Option[Cancellable] = None

  lazy val typewriter = context.actorSelection("../typewriter")
  lazy val cartographer = context.actorSelection("../cartographer")

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒
      pings = Some(context.system.scheduler.schedule(5 seconds, 5 seconds, self, Ping))

    case DetachUi ⇒
      pings.foreach(_.cancel())

    case Ping ⇒
      cartographer ! Cartographer.QueryLastLocation

    case Some(l: LatLng) ⇒
      apis.foursquareApi.nearbyVenues(l.latitude, l.longitude, 100).go.map(Venues) pipeTo self
      apis.flickrApi.nearbyPhotos(l.latitude, l.longitude, 10).go.map(Photos) pipeTo self

    case Venues(venues) ⇒ withUi { f ⇒
      f.venues.get.setData(venues)
    }

    case Photos(photos) ⇒ withUi { f ⇒
      f.photos.get.setData(photos)
    }

    case _ ⇒
  }
}
