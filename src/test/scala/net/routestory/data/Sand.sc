import com.javadocmd.simplelatlng.LatLng
import net.routestory.data.Story.Chapter
import net.routestory.data.{Clustering, Story, Timed}

val chapter = Chapter.empty
  .withElement(Timed(0, Story.TextNote("asd")))
  .withElement(Timed(100, Story.TextNote("qwe")))
  .withLocation(Timed(0, new LatLng(0, 0)))
  .withLocation(Timed(100, new LatLng(0, 0)))

Clustering.cluster(chapter)
