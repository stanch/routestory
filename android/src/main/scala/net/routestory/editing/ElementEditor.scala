package net.routestory.editing

import akka.actor.ActorSelection
import macroid.FullDsl._
import macroid.{ AppContext, ActivityContext, Ui }
import net.routestory.browsing.story.ElementViewer
import net.routestory.data.{ Timed, Story }

case class ElementEditor(element: Timed[Story.KnownElement])(editor: ActorSelection)(implicit ctx: ActivityContext, appCtx: AppContext) {
  def onClick = ElementViewer.show(element.data)

  def onRemove = dialog("Do you want to delete this element?") <~
    positiveOk(Ui(editor ! Editor.RemoveElement(element))) <~
    negativeCancel(Ui.nop) <~
    speak
}
