package net.routestory.needs

import java.io.File
import org.needs._
import play.api.data.mapping.json.Rules._

import net.routestory.model._

trait Needs { self: Shared with Endpoints with LoadingFormats ⇒
  def author(id: String): Resolvable[Author] =
    Source[Author].from(LocalAuthor(id)) orElse
      Source[Author].from(RemoteAuthor(id))

  def story(id: String): Resolvable[Story] =
    Source[Story].from(LocalStory(id)) orElse
      Source[Story].from(RemoteStory(id))

  def latest(num: Int): Resolvable[Latest] =
    Source[Latest].from(RemoteLatest(num))

  def search(query: String, limit: Int = 4, bookmark: Option[String] = None): Resolvable[Searched] =
    Source[Searched].from(RemoteSearch(query, limit, bookmark))

  def tagged(tag: String, limit: Int = 4, bookmark: Option[String] = None): Resolvable[Searched] =
    Source[Searched].from(RemoteTagged(tag, limit, bookmark))

  def tags: Resolvable[List[Tag]] =
    Source[List[Resolvable[Tag]]].from(RemoteTags()).flatMap(Resolvable.fromList)

  def media(url: String): Resolvable[File] = if (url.startsWith("/")) {
    Source[File].from(LocalTempMedia(url))
  } else if (url.startsWith("http")) {
    Source[File].from(LocalCachedMedia(url)) orElse Source[File].from(RemoteExternalMedia(url))
  } else {
    Source[File].from(LocalCachedMedia(url)) orElse Source[File].from(RemoteMedia(url))
  }
}
