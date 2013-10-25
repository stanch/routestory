package net.routestory.bag

import spray.http.Uri
import spray.http.Uri.Query

object Couch {
  def viewUri(designDoc: String, view: String, args: (String, String)*) =
    Uri.from(path = s"/story2/_design/$designDoc/_view/$view", query = Query(args: _*))

  def docUri(id: String) =
    Uri.from(path = s"/story2/$id")

  def attUri(id: String, attachmentId: String) =
    Uri.from(path = s"/story2/$id/$attachmentId")
}
