package net.routestory.external

import com.google.android.gms.maps.model.LatLng
import org.needs.json.JsonEndpoint
import org.needs.http.{ AndroidJsonClient, HttpEndpoint }
import scala.concurrent.{ Future, ExecutionContext }
import net.routestory.needs.EndpointLogging
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.needs.{ Need, Fulfillable }

// format: OFF

object Flickr {
  case class Photo(id: String, url: String, title: String, owner: Owner)
  case class Owner(id: String, name: String)

  implicit val photoReads = Fulfillable.reads[Photo] {
    (__ \ 'photo \ 'id).read[String] and
    (__ \ 'photo \ 'id).read[String] and
    (__ \ 'photo \ 'title).read[String] and
    (__ \ 'photo \ 'owner).read[String].map(NeedOwner)
  }

  implicit val ownerReads = Fulfillable.reads[Owner] {
    (__ \ 'person \ 'nsid).read[String] and
    (__ \ 'person \ 'username).read[String]
  }

  abstract class FlickrEndpoint(method: String)(args: (String, String)*)
    extends JsonEndpoint
    with HttpEndpoint
    with AndroidJsonClient
    with EndpointLogging {

    val asyncHttpClient = net.routestory.needs.HttpClient.client
    protected def fetch(implicit ec: ExecutionContext) =
      client("http://api.flickr.com/services", Map(args: _*) ++ Map(
        "method" → method,
        "api_key" → "b8888816f155ed6f419b9a2348e16fee",
        "format" → "json"
      ))
  }

  case class NearbyPhotos(location: LatLng) extends FlickrEndpoint("flickr.photos.search")(
    "lat" → location.latitude.toString,
    "lon" → location.longitude.toString,
    "radius" → "100"
  )

  case class OwnerInfo(id: String) extends FlickrEndpoint("flickr.people.getInfo")(
    "user_id" → id
  )

  case class NeedNearbyPhotos(location: LatLng) extends Need[List[Photo]] {
    use(NearbyPhotos(location))
    from {
      case e @ NearbyPhotos(`location`) ⇒ e.probe.flatMap { js ⇒
        Fulfillable.jumpList((js \ "photos").as[List[Fulfillable[Photo]]])
      }
    }
  }

  case class NeedOwner(id: String) extends Need[Owner] {
    use(OwnerInfo(id))
    from {
      case e @ OwnerInfo(`id`) ⇒ e.probeAs[Owner]
    }
  }
}
