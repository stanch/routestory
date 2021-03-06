package net.routestory.recording

import akka.actor._
import akka.pattern.pipe
import android.location.Location
import macroid.akkafragments.FragmentActor
import net.routestory.ui.Tweaks
import net.routestory.{ RouteStoryApp, Apis }
import net.routestory.data.Story
import net.routestory.util.Implicits._
import scala.concurrent.duration._
import macroid.Tweaking._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object Suggester {
  case object Update
  case class FoursquareVenues(venues: List[Story.FoursquareVenue])
  case class FlickrPhotos(photos: List[Story.FlickrPhoto])
  case class InstagramPhotos(photos: List[Story.InstagramPhoto])

  case class Add(element: Story.KnownElement)
  case class Dismiss(element: Story.KnownElement)

  def props(app: RouteStoryApp) = Props(new Suggester(app))
}

class Suggester(app: RouteStoryApp) extends FragmentActor[AddMediaFragment] with ActorLogging {
  import FragmentActor._
  import Suggester._

  lazy val typewriter = context.actorSelection("../typewriter")
  lazy val cartographer = context.actorSelection("../cartographer")

  var dismissed = Set.empty[Story.KnownElement]

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
      self ! Update

    case Update ⇒
      if (app.suggestionsEnabled) {
        cartographer ! Cartographer.QueryLastLocation
      } else {
        withUi(_.swiper <~ Tweaks.stopRefresh)
      }

    case Add(element) ⇒
      typewriter ! Typewriter.Element(element)
      dismissed += element
      withUi(_.hideSuggestion(element))

    case Dismiss(element) ⇒
      dismissed += element
      withUi(_.hideSuggestion(element))

    case None ⇒
      // backoff
      context.system.scheduler.scheduleOnce(5 seconds, self, Update)

    case Some(location: Location) ⇒
      log.debug("Calling external APIs")

      val venues = app.foursquareApi.nearbyVenues(location, 100).go.recoverWithNil.map(FoursquareVenues)
      val flickrPhotos = app.flickrApi.nearbyPhotos(location, 1).go.recoverWithNil.map(FlickrPhotos)
      val instagramPhotos = app.instagramApi.nearbyPhotos(location, 100).go.recoverWithNil.map(InstagramPhotos)
      venues zip flickrPhotos zip instagramPhotos pipeTo self

    case ((FoursquareVenues(venues), FlickrPhotos(flickrPhotos)), InstagramPhotos(instagramPhotos)) ⇒
      val elements = interleave(List(
        venues.filterNot(dismissed),
        flickrPhotos.filterNot(dismissed),
        instagramPhotos.filterNot(dismissed)
      )).take(10)
      withUi(_.showSuggestions(elements))

    case _ ⇒
  }
}
