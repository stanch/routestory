package net.routestory.couch

import com.couchbase.lite.Document.DocumentUpdater
import com.couchbase.lite.UnsavedRevision
import net.routestory.data.Story
import net.routestory.json.JsonWrites._
import play.api.data.mapping.To
import play.api.libs.json.{JsObject, JsString}

trait Native extends JsonHelpers { self: Api ⇒
  def createStory(story: Story) = {
    val json = (To[Story, JsObject](story) + ("type" → JsString("story"))).toJavaMap
    val doc = couch.getDocument(story.id)
    doc.putProperties(json)
  }

  def updateStory(story: Story) = {
    val json = (To[Story, JsObject](story) + ("type" → JsString("story"))).toJavaMap
    val doc = couch.getExistingDocument(story.id)
    doc.update(new DocumentUpdater {
      def update(rev: UnsavedRevision) = {
        rev.setUserProperties(json)
        true
      }
    })
  }

  def deleteStory(id: String) = {
    val doc = couch.getExistingDocument(id)
    doc.delete()
  }

  def isLocal(id: String) = couch.getExistingDocument(id) != null
}
