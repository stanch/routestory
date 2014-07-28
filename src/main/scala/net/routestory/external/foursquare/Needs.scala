package net.routestory.external.foursquare

import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.Story
import resolvable.{Resolvable, Source}
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonRules ⇒
  def nearbyVenues(location: LatLng, radius: Int) = Source[List[Resolvable[Story.FoursquareVenue]]]
    .fromPath(NearbyVenues(location.getLatitude, location.getLongitude, radius))(_ \ "response" \ "venues")
    .flatMap(Resolvable.fromList)
}
