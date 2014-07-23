package net.routestory.recording.suggest

import akka.actor._
import akka.pattern.pipe
import android.os.Bundle
import android.view.{ LayoutInflater, ViewGroup }
import com.etsy.android.grid.StaggeredGridView
import com.google.android.gms.maps.model.LatLng
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.ListTweaks
import macroid.viewable.FillableViewableAdapter
import macroid.{ ActivityContext, AppContext, Ui }
import net.routestory.Apis
import net.routestory.data.Story
import net.routestory.recording.Cartographer
import net.routestory.ui.{ RouteStoryFragment, Styles }
import net.routestory.util.Implicits._
import net.routestory.viewable.StoryElementViewable
import resolvable.{ flickr, foursquare }

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SuggestionsFragment extends RouteStoryFragment with AkkaFragment {
  lazy val actor = Some(actorSystem.actorSelection("/user/suggester"))
  lazy val typewriter = actorSystem.actorSelection("/user/typewriter")

  lazy val viewables = new StoryElementViewable(200 dp)
  lazy val adapter = FillableViewableAdapter(viewables)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    w[StaggeredGridView] <~ Styles.grid <~ ListTweaks.adapter(adapter)
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

  var suggest = Vector.empty[Story.KnownElement]

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

    case Venues(venues) ⇒
      val elements = venues.map(v ⇒ Story.FoursquareVenue(v.id, v.name, new LatLng(v.lat, v.lng)))
      suggest = (suggest ++ (elements diff suggest)).take(10)
      withUi(f ⇒ Ui {
        f.adapter.clear()
        f.adapter.addAll(suggest.asJava)
      })

    case Photos(photos) ⇒
    //      val elements = photos.map(p ⇒ Story.FlickrPhoto(p.id, p.title, p.url))
    //      withUi(f ⇒ Ui {
    //        f.photos.get.setData(photos)
    //      })

    case _ ⇒
  }
}
