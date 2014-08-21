import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.Story.Chapter
import net.routestory.data.{Sandbox, Clustering, Story, Timed}

import scala.concurrent.ExecutionContext.Implicits.global

val sand = new Sandbox

val x = sand.insta.nearbyPhotos(new LatLng(38.4, 9.8), 5000).go

x onComplete println
