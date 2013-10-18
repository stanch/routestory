package net.routestory.bag

import spray.http.Uri
import spray.http.Uri.Query

object Lucene {
  def escape(s: String) = List("\\", "+", "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "~", "*", "?", ":", "\"").foldLeft(s) {
    (x, c) ⇒ x.replace(c, "\\" + c)
  }
  def exact(field: String, q: String) = s"""($field:"${escape(q)}")"""
  def fuzzy(field: String, q: String) = s"""($field:${escape(q)}~)"""
  def uri(kvp: (String, String)*) = Uri.from(path = "/story2/_design/Story/_search/byEverything", query = Query(kvp: _*))
}
