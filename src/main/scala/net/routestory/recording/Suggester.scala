package net.routestory.recording

import akka.actor._
import akka.pattern.pipe
import android.location.Location
import macroid.akkafragments.FragmentActor
import net.routestory.Apis
import net.routestory.data.Story
import net.routestory.util.Implicits._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object Suggester {
  case object Update
  case class FoursquareVenues(venues: List[Story.FoursquareVenue])
  case class FlickrPhotos(photos: List[Story.FlickrPhoto])
  case class InstagramPhotos(photos: List[Story.InstagramPhoto])

  def props(apis: Apis) = Props(new Suggester(apis))
}

class Suggester(apis: Apis) extends FragmentActor[AddMediaFragment] with ActorLogging {
  import FragmentActor._
  import Suggester._

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
      self ! Update

    case Update ⇒
      cartographer ! Cartographer.QueryLastLocation

    case None ⇒
      // backoff
      context.system.scheduler.scheduleOnce(5 seconds, self, Update)

    case Some(location: Location) ⇒
      log.debug("Calling external APIs")

      val venues = apis.foursquareApi.nearbyVenues(location, 100).go.recoverWithNil.map(FoursquareVenues)
      val flickrPhotos = apis.flickrApi.nearbyPhotos(location, 1).go.recoverWithNil.map(FlickrPhotos)
      val instagramPhotos = apis.instagramApi.nearbyPhotos(location, 100).go.recoverWithNil.map(InstagramPhotos)
      venues zip flickrPhotos zip instagramPhotos pipeTo self

    case ((FoursquareVenues(venues), FlickrPhotos(flickrPhotos)), InstagramPhotos(instagramPhotos)) ⇒
      val elements = interleave(List(venues, flickrPhotos, instagramPhotos)).take(10)
      withUi(_.showSuggestions(elements))

    case _ ⇒
  }
}
