package net.routestory.external.instagram

import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.Story
import resolvable.{Resolvable, Source}
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonRules =>
  def webApi: net.routestory.web.Api

  def nearbyPhotos(location: LatLng, distance: Int) = Source[List[Resolvable[Story.InstagramPhoto]]]
    .fromPath(NearbyPhotos(location.getLatitude, location.getLongitude, distance))(_ \ "data")
    .flatMap(Resolvable.fromList)

  def media(url: String) = webApi.media(url)
}
