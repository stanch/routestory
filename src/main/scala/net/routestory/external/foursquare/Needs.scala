package net.routestory.external.foursquare

import net.routestory.data.Story
import resolvable.{Resolvable, Source}
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonRules ⇒
  def nearbyVenues(lat: Double, lng: Double, radius: Int) = Source[List[Resolvable[Story.FoursquareVenue]]]
    .fromPath(NearbyVenues(lat, lng, radius))(_ \ "response" \ "venues")
    .flatMap(Resolvable.fromList)
}
