package net.routestory.recording.suggest

import akka.actor._
import akka.pattern.pipe
import android.app.Activity
import android.location.Location
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.view.{ View, LayoutInflater, ViewGroup }
import android.widget.AdapterView
import com.etsy.android.grid.StaggeredGridView
import com.google.android.gms.maps.model.LatLng
import macroid.FullDsl._
import macroid.akkafragments.{ AkkaFragment, FragmentActor }
import macroid.contrib.ListTweaks
import macroid.{ ActivityContext, AppContext, Ui }
import net.routestory.Apis
import net.routestory.data.Story
import net.routestory.recording.{ Typewriter, RecordFragment, RecordActivity, Cartographer }
import net.routestory.ui.{ RouteStoryFragment, Styles }
import net.routestory.util.Implicits._
import net.routestory.viewable.{ CardListable, StoryElementListable }
import macroid.viewable._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

class SuggestionsFragment extends RouteStoryFragment with RecordFragment {
  lazy val actor = actorSystem.map(_.actorSelection("/user/suggester"))
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))

  var grid = slot[StaggeredGridView]
  var swiper = slot[SwipeRefreshLayout]

  def showElements(elements: List[Story.KnownElement]) = {
    val listable = CardListable.cardListable(
      new StoryElementListable(200 dp).storyElementListable)

    if (getUserVisibleHint) {
      typewriter.foreach(_ ! Typewriter.Suggestions(elements.length))
    }

    val updateGrid = grid <~ listable.listAdapterTweak(elements) <~
      FuncOn.itemClick[StaggeredGridView] { (_: AdapterView[_], _: View, index: Int, _: Long) ⇒
        Ui(typewriter.foreach(_ ! Typewriter.Element(elements(index))))
      }

    updateGrid ~ (swiper <~ Styles.stopRefresh)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    l[SwipeRefreshLayout](
      w[StaggeredGridView] <~ Styles.grid <~ wire(grid)
    ) <~ Styles.swiper <~ wire(swiper) <~ On.refresh[SwipeRefreshLayout](Ui(actor.foreach(_ ! Suggester.Update)))
  }

  override def onStart() = {
    super.onStart()
    actor.foreach(_ ! FragmentActor.AttachUi(this))
  }

  override def onStop() = {
    super.onStop()
    actor.foreach(_ ! FragmentActor.DetachUi(this))
  }

  override def setUserVisibleHint(isVisibleToUser: Boolean): Unit = {
    super.setUserVisibleHint(isVisibleToUser)
    if (isVisibleToUser) {
      grid.flatMap(x ⇒ Option(x.getAdapter)).map(_.getCount) foreach { n ⇒
        typewriter.foreach(_ ! Typewriter.Suggestions(n))
      }
    }
  }
}

object Suggester {
  case object Update
  case class FoursquareVenues(venues: List[Story.FoursquareVenue])
  case class FlickrPhotos(photos: List[Story.FlickrPhoto])
  case class InstagramPhotos(photos: List[Story.InstagramPhoto])

  def props(apis: Apis) = Props(new Suggester(apis))
}

class Suggester(apis: Apis) extends FragmentActor[SuggestionsFragment] with ActorLogging {
  import macroid.akkafragments.FragmentActor._
  import net.routestory.recording.suggest.Suggester._

  lazy val typewriter = context.actorSelection("../typewriter")
  lazy val cartographer = context.actorSelection("../cartographer")

  var suggest = List.empty[Story.KnownElement]

  def interleave[A](lists: List[List[A]]): List[A] = lists.flatMap(_.take(1)) match {
    case Nil ⇒ Nil
    case heads ⇒ heads ::: interleave(lists.map(_.drop(1)))
  }

  implicit class RichFuture[A](future: Future[List[A]]) {
    def recoverWithNil(implicit ec: ExecutionContext) = future.recover {
      case NonFatal(t) ⇒ t.printStackTrace(); Nil
    }
  }

  def receive = receiveUi andThen {
    case AttachUi(_) ⇒
      withUi(_.swiper <~ Styles.startRefresh)
      self ! Update

    case Update ⇒
      cartographer ! Cartographer.QueryLastLocation

    case None ⇒
      // re-request last location
      cartographer ! Cartographer.QueryLastLocation

    case Some(location: Location) ⇒
      log.debug("Calling external APIs")

      val venues = apis.foursquareApi.nearbyVenues(location, 100).go.recoverWithNil.map(FoursquareVenues)
      val flickrPhotos = apis.flickrApi.nearbyPhotos(location, 1).go.recoverWithNil.map(FlickrPhotos)
      val instagramPhotos = apis.instagramApi.nearbyPhotos(location, 1000).go.recoverWithNil.map(InstagramPhotos)
      venues zip flickrPhotos zip instagramPhotos pipeTo self

    case ((FoursquareVenues(venues), FlickrPhotos(flickrPhotos)), InstagramPhotos(instagramPhotos)) ⇒
      val elements = interleave(List(venues, flickrPhotos, instagramPhotos)).take(10)
      withUi(_.showElements(elements))

    case _ ⇒
  }
}
