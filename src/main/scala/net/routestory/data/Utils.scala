package net.routestory.data

import java.io.File

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object Utils {
  /** Replace urls with filenames */
  def withJustFilenames(story: Story)(implicit ec: ExecutionContext): Future[(Story, List[(File, String)])] = {
    val chapters = Future.sequence(story.chapters.map { chapter ⇒
      val elements = Future.sequence(chapter.elements.map {
        case t @ Timed(_, e: Story.MediaElement) ⇒
          e.data map { file ⇒
            t.copy(data = e.withFile(new File(file.getName))) → List(file → file.getName)
          } recover {
            case NonFatal(_) ⇒ t → Nil
          }
        case t ⇒
          Future.successful(t → Nil)
      })
      elements.map(e ⇒ chapter.copy(elements = e.map(_._1)) → e.flatMap(_._2))
    })
    chapters.map(c ⇒ story.copy(chapters = c.map(_._1)) → c.flatMap(_._2))
  }
}
