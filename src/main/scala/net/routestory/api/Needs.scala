package net.routestory.api

import java.io.File

import net.routestory.data._
import net.routestory.json.JsonRules
import resolvable.{Resolvable, Source}
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonRules ⇒
  def author(id: String): Resolvable[Author] =
      Source[Author].from(RemoteAuthor(id))

  def story(id: String): Resolvable[Story] =
      Source[Story].from(RemoteStory(id))

  def story(preview: StoryPreview): Resolvable[Story] =
    story(preview.id)

  def latest(num: Int): Resolvable[Latest] =
    Source[Latest].from(RemoteLatest(num))

  def search(query: String, limit: Int = 4, bookmark: Option[String] = None): Resolvable[Searched] =
    Source[Searched].from(RemoteSearch(query, limit, bookmark))

  def tagged(tag: String, limit: Int = 4, bookmark: Option[String] = None): Resolvable[Searched] =
    Source[Searched].from(RemoteTagged(tag, limit, bookmark))

  def tags: Resolvable[List[Tag]] =
    Source[List[Resolvable[Tag]]].fromPath(RemoteTags())(_ \ "rows").flatMap(Resolvable.fromList)

  def media(url: String): Resolvable[File] = if (url.startsWith("/")) {
    Source[File].from(LocalTempMedia(url))
  } else if (url.startsWith("http")) {
    Source[File].from(LocalCachedMedia(url)) orElse Source[File].from(RemoteExternalMedia(url))
  } else {
    Source[File].from(LocalCachedMedia(url)) orElse Source[File].from(RemoteMedia(url))
  }
}
