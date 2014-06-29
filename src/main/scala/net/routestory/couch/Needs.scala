package net.routestory.couch

import java.io.File

import net.routestory.data.{Story, Author}
import net.routestory.json.JsonRules
import resolvable.{Source, Resolvable}

trait Needs { self: Endpoints with JsonRules ⇒
  def webApi: net.routestory.web.Api

  def story(id: String): Resolvable[Story] =
    Source[Story].from(LocalStory(id))

  def author(id: String): Resolvable[Author] =
    Source[Author].from(LocalAuthor(id))

  def media(url: String): Resolvable[File] =
    webApi.media(url)
}
