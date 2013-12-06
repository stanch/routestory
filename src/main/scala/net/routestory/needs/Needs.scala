package net.routestory.needs

import org.needs._
import net.routestory.model2._

case class NeedAuthor(id: String) extends Need[Author] with rest.RestNeed[Author] {
  use { RemoteAuthor(id) }
  from {
    singleResource[RemoteAuthor]
  }
}

case class NeedStory(id: String) extends Need[Story] with rest.RestNeed[Story] {
  use { RemoteStory(id) }
  from {
    singleResource[RemoteStory]
  }
}

case class NeedLatest(num: Int) extends Need[Latest] {
  use { LatestStories(num) }
  from {
    case e @ LatestStories(_) ⇒ e.asFulfillable[Latest]
  }
}

case class NeedSearch(query: String, limit: Int = 4, bookmark: Option[String] = None) extends Need[Searched] {
  use { SearchStories(query, limit, bookmark) }
  from {
    case e @ SearchStories(`query`, `limit`, `bookmark`) ⇒ e.asFulfillable[Searched]
  }
}

case class NeedTagged(tag: String, limit: Int = 4, bookmark: Option[String] = None) extends Need[Searched] {
  use { TaggedStories(tag, limit, bookmark) }
  from {
    case e @ TaggedStories(`tag`, `limit`, `bookmark`) ⇒ e.asFulfillable[Searched]
  }
}

case class NeedTags() extends Need[List[Tag]] {
  use { PopularTags() }
  from {
    case e @ PopularTags() ⇒ e.asFulfillable[List[Tag]]
  }
}
