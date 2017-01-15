package net.routestory.external.foursquare

import java.util.Locale

import resolvable.json.HttpJsonEndpoint

import scala.concurrent.ExecutionContext

trait Endpoints {
  def webApi: net.routestory.web.Api
  def clientId: String
  def clientSecret: String

  abstract class FoursquareEndpoint(url: String)(args: (String, String)*) extends HttpJsonEndpoint {
    val logger = webApi.endpointLogger
    val client = webApi.httpClient

    protected def fetch(implicit ec: ExecutionContext) =
      client.getJson(s"https://api.foursquare.com/v2/$url", Map(args: _*) ++ Map(
        "client_id" → clientId,
        "client_secret" → clientSecret,
        "v" → "20140114"
      ))
  }

  case class NearbyVenues(lat: Double, lng: Double, radius: Int) extends FoursquareEndpoint("venues/search")(
    "ll" → "%f,%f".formatLocal(Locale.US, lat, lng),
    "intent" → "browse",
    "radius" → radius.toString
  )
}
