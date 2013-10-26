package net.routestory.bag

import spray.http.Uri
import spray.http.Uri.Query
import spray.client.pipelining._
import play.api.libs.json._
import spray.httpx.marshalling.Marshaller

object Couch {
  def viewReq(designDoc: String, view: String, args: (String, String)*) =
    Get(Uri.from(path = s"/story2/_design/$designDoc/_view/$view", query = Query(args: _*)))

  def searchReq(designDoc: String, search: String, args: (String, String)*) =
    Get(Uri.from(path = s"/story2/_design/$designDoc/_search/$search", query = Query(args: _*)))

  def docUri(id: String) =
    Uri.from(path = s"/story2/$id")

  def attReq(id: String, attachmentId: String) =
    Get(Uri.from(path = s"/story2/$id/$attachmentId"))

  def docsReq(ids: List[String])(implicit m: Marshaller[JsValue]) = Post(
    Uri.from(path = "/story2/_all_docs", query = Query("include_docs" → "true")),
    Json.obj("keys" → Json.toJson(ids))
  )
}
