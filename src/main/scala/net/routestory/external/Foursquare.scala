package net.routestory.external

import java.util.Locale

import scala.concurrent.ExecutionContext

import com.google.android.gms.maps.model.LatLng
import org.needs.Need
import org.needs.http.{ AndroidJsonClient, HttpEndpoint }
import org.needs.json.JsonEndpoint
import play.api.libs.functional.syntax._
import play.api.libs.json._

object Foursquare {
  case class Venue(id: String, name: String, lat: Double, lng: Double)
  implicit val venueReads = (
    (__ \ 'id).read[String] and
    (__ \ 'name).read[String] and
    (__ \ 'location \ 'lat).read[Double] and
    (__ \ 'location \ 'lng).read[Double]
  )(Venue)

  case class NearbyVenues(location: LatLng) extends JsonEndpoint with HttpEndpoint with AndroidJsonClient {
    val asyncHttpClient = net.routestory.needs.Client.client
    protected def fetch(implicit ec: ExecutionContext) =
      client("https://api.foursquare.com/v2/venues/search", Map(
        "client_id" → "0TORHPL0MPUG24YGBVNINGV2LREZJCD0XBCDCBMFC0JPDO05",
        "client_secret" → "SIPSHLBOLADA2HW3RT44GE14OGBDNSM0VPBN4MSEWH2E4VLN",
        "ll" → "%f,%f".formatLocal(Locale.US, location.latitude, location.longitude),
        "v" → "20130920",
        "intent" → "browse",
        "radius" → "100"
      ))
  }

  case class NeedNearbyVenues(location: LatLng) extends Need[Seq[Venue]] {
    use(NearbyVenues(location))
    from {
      case e @ NearbyVenues(`location`) ⇒ e.probe map { js ⇒
        (js \ "response" \ "venues").as[Seq[Venue]]
      }
    }
  }
}