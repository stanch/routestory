package net.routestory.couch

import net.routestory.data.Story
import net.routestory.json.JsonWrites._
import play.api.data.mapping.To
import play.api.libs.json.{JsString, JsObject}

trait Native extends JsonHelpers { self: Api ⇒
  def saveStory(story: Story) = {
    val json = (To[Story, JsObject](story) + ("type" → JsString("story"))).toJavaMap
    val doc = couch.getDocument(story.id)
    doc.putProperties(json)
  }

  def delete(id: String) = couch.deleteLocalDocument(id)

  def isLocal(id: String) = couch.getExistingDocument(id) != null
}
