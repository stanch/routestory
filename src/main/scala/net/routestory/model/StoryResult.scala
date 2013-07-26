package net.routestory.model

import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.annotate.JsonIgnoreProperties
import org.codehaus.jackson.annotate.JsonProperty
import scala.collection.JavaConverters._

object StoryResult {
  class Geometry {
    @JsonProperty("type") val `type`: String = "MultiPoint"
    @JsonProperty("coordinates") var coordinates: java.util.List[java.util.List[Double]] = null
  }
  object Geometry {
    def apply(locationss: java.util.List[Story.LocationData]) = {
      val geom = new Geometry
      geom.coordinates = locationss.asScala.map(_.coordinates.toList.asJava).asJava
      geom
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