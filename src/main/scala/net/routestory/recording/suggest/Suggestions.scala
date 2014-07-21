package net.routestory.recording.suggest

import akka.actor._
import akka.pattern.pipe
import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }
import android.widget._
import com.google.android.gms.maps.model.LatLng
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.Ui
import macroid.{ ActivityContext, AppContext }
import net.routestory.Apis
import net.routestory.recording.Cartographer
import net.routestory.ui.{ CollapsedListView, RouteStoryFragment }
import net.routestory.viewable.Suggestables
import resolvable.{ flickr, foursquare }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SuggestionsFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/suggester"))
  lazy val typewriter = actorSystem.actorSelection("/user/typewriter")

  lazy val suggestables = Suggestables(app, typewriter)

  var venues = slot[CollapsedListView[foursquare.Venue]]
  var photos = slot[CollapsedListView[flickr.Photo]]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[ScrollView]( //      l[VerticalLinearLayout](
    //        new CollapsedListView[foursquare.Venue](
    //          w[TextView] <~ TextSize.large <~ p8dding <~ text("Foursquare")
    //        ) <~ wire(venues),
    //        new CollapsedListView[flickr.Photo](
    //          w[TextView] <~ TextSize.large <~ p8dding <~ text("Flickr")
    //        ) <~ wire(photos)
    //      )
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
  import macroid.akkafragments.FragmentActor._
  import net.routestory.recording.suggest.Suggester._

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

    case Venues(venues) ⇒ withUi(f ⇒ Ui {
      f.venues.get.setData(venues)
    })

    case Photos(photos) ⇒ withUi(f ⇒ Ui {
      f.photos.get.setData(photos)
    })

    case _ ⇒
  }
}
