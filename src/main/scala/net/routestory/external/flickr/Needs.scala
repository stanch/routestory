package net.routestory.external.flickr

import net.routestory.data.Story
import resolvable.{Resolvable, Source}
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonRules ⇒
  def webApi: net.routestory.web.Api

  def nearbyPhotos(lat: Double, lng: Double, radius: Int) = Source[List[Resolvable[Story.FlickrPhoto]]]
    .fromPath(NearbyPhotos(lat, lng, radius))(_ \ "photos" \ "photo")
    .flatMap(Resolvable.fromList)

  def media(url: String) = webApi.media(url)
}
