package net.routestory.external.flickr

import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.Story
import resolvable.{Resolvable, Source}
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonRules ⇒
  def webApi: net.routestory.web.Api

  def nearbyPhotos(location: LatLng, radius: Int) = Source[List[Resolvable[Story.FlickrPhoto]]]
    .fromPath(NearbyPhotos(location.getLatitude, location.getLongitude, radius))(_ \ "photos" \ "photo")
    .flatMap(Resolvable.fromList)

  def media(url: String) = webApi.media(url)
}
