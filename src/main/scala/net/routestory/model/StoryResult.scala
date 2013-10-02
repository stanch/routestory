package net.routestory.model

import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.annotate.JsonIgnoreProperties
import org.codehaus.jackson.annotate.JsonProperty
import scala.collection.JavaConversions._
import com.google.android.gms.maps.model.LatLng

object StoryResult {
  class Geometry {
    @JsonIgnore
    lazy val asLatLngs = coordinates.map(x ⇒ new LatLng(x(0), x(1))).toList

    @JsonProperty("type") val `type`: String = "MultiPoint"
    @JsonProperty("coordinates") var coordinates: java.util.List[Array[Double]] = _
  }
  object Geometry {
    def apply(locations: java.util.List[Story.LocationData]) = new Geometry {
      coordinates = locations.map(_.coordinates)
    }
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class StoryResult {
  @JsonProperty("_id") var id: String = _
  @JsonProperty("title") var title: String = _
  @JsonProperty("tags") var tags: Array[String] = _
  @JsonProperty("author") var authorId: String = _
  @JsonProperty("geometry") var geometry: StoryResult.Geometry = _
  @JsonProperty("locations") var locations: java.util.List[Story.LocationData] = _
  @JsonIgnore var author: Author = _

  @JsonIgnore
  lazy val geom = Option(geometry) getOrElse {
    StoryResult.Geometry(locations)
  }
}