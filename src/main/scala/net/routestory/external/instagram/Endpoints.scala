package net.routestory.external.instagram

import java.util.Locale

import resolvable.json.HttpJsonEndpoint

import scala.concurrent.ExecutionContext

trait Endpoints {
  def webApi: net.routestory.web.Api
  def clientId: String

  abstract class InstagramEndpoint(url: String)(args: (String, String)*) extends HttpJsonEndpoint {
    val logger = webApi.endpointLogger
    val client = webApi.httpClient

    protected def fetch(implicit ec: ExecutionContext) =
      client.getJson(s"https://api.instagram.com/v1/$url", Map(args: _*) ++ Map(
        "client_id" → clientId
      ))
  }

  case class NearbyPhotos(lat: Double, lng: Double, distance: Int) extends InstagramEndpoint("media/search")(
    "lat" → "%f".formatLocal(Locale.US, lat),
    "lng" → "%f".formatLocal(Locale.US, lng),
    "intent" → "browse",
    "distance" → distance.toString
  )
}
