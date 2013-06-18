package net.routestory.model;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown=true)
public class StoryResult {
	@JsonProperty("_id")
	public String id;
	@JsonProperty("title")
	public String title;
	@JsonProperty("tags")
	public String[] tags;
	@JsonProperty("author")
	public String authorId;
	@JsonProperty("geometry")
	public Geometry geometry;
	
	@JsonIgnore
	public Author author;
	
	public static class Geometry {
		@JsonProperty("type")
		public final String type = "MultiPoint";
		@JsonProperty("coordinates")
		public List<List<Double>> coordinates;
	}
}
