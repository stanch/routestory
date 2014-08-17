package net.routestory.couch

import java.io.File

import net.routestory.data.{Story, Author}
import net.routestory.json.JsonRules
import resolvable.{Source, Resolvable}
import play.api.data.mapping.json.Rules._

trait Needs { self: Endpoints with JsonRules ⇒
  def webApi: net.routestory.web.Api

  def story(id: String): Resolvable[Story] =
    Source[Story].from(LocalStory(id)) orElse
    webApi.story(id)

  def author(id: String): Resolvable[Author] =
    Source[Author].from(LocalAuthor(id)) orElse
    webApi.author(id)

  def media(url: String): Resolvable[File] =
    webApi.media(url)

  def localStories(viewName: String): Resolvable[List[Story]] =
    Source[List[Resolvable[Story]]].from(LocalView(viewName))
      .flatMap(Resolvable.fromList)
}
