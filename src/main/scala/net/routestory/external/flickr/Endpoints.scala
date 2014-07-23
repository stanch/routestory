package net.routestory.external.flickr

import play.api.libs.json.JsString
import resolvable.json.HttpJsonEndpoint

import scala.concurrent.ExecutionContext

case class FlickrApiException(message: String) extends Exception(message)

trait Endpoints {
  def webApi: net.routestory.web.Api
  def apiKey: String

  abstract class FlickrEndpoint(method: String)(args: (String, String)*) extends HttpJsonEndpoint {
    val logger = webApi.endpointLogger
    val client = webApi.httpClient

    protected def fetch(implicit ec: ExecutionContext) =
      client.getJson("https://api.flickr.com/services/rest", Map(args: _*) ++ Map(
        "method" → method,
        "api_key" → apiKey,
        "format" → "json",
        "nojsoncallback" → "1"
      )) map { js ⇒
        if ((js \ "stat") == JsString("fail")) throw FlickrApiException((js \ "message").as[String]) else js
      }
  }

  case class NearbyPhotos(lat: Double, lng: Double, radius: Int) extends FlickrEndpoint("flickr.photos.search")(
    "lat" → lat.toString,
    "lon" → lat.toString,
    "radius" → radius.toString
  )

  case class PersonInfo(id: String) extends FlickrEndpoint("flickr.people.getInfo")(
    "user_id" → id
  )
}
