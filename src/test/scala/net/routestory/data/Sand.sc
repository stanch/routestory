import net.routestory.data.{Utils, Clustering, Sandbox}
import net.routestory.zip.Save
import resolvable.Resolvable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
val sand = new Sandbox
val results = for {
  previews ← sand.api.tagged("study", limit = 1)
  complete ← Resolvable.fromList(previews.stories.map(sand.api.story))
} yield complete
val story = Await.result(results.go, 10 seconds).head
val chapter = story.chapters.head
Save(story) onComplete println
println("asd")

