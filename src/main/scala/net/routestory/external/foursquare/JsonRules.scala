package net.routestory.external.foursquare

import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.Story
import play.api.libs.json.JsValue
import resolvable.Resolvable
import play.api.data.mapping.json.Rules._

trait JsonRules {
  implicit val venueRule = Resolvable.rule[JsValue, Story.FoursquareVenue] { __ ⇒
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    ((__ \ "location" \ "lat").read[Double] and
     (__ \ "location" \ "lat").read[Double])((lat, lng) ⇒ new LatLng(lat, lng))
  }
}
