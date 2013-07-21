package net.routestory.model

import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.annotate.JsonIgnoreProperties
import org.codehaus.jackson.annotate.JsonProperty

object StoryResult {
  class Geometry {
    @JsonProperty("type") val `type`: String = "MultiPoint"
    @JsonProperty("coordinates") var coordinates: java.util.List[java.util.List[Double]] = null
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class StoryResult {
  @JsonProperty("_id") var id: String = _
  @JsonProperty("title") var title: String = _
  @JsonProperty("tags") var tags: Array[String] = _
  @JsonProperty("author") var authorId: String = _
  @JsonProperty("geometry") var geometry: StoryResult.Geometry = _
  @JsonIgnore var author: Author = _
}