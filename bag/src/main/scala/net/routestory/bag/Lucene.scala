package net.routestory.bag

object Lucene {
  def escape(s: String) = List("\\", "+", "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "~", "*", "?", ":", "\"").foldLeft(s) {
    (x, c) ⇒ x.replace(c, "\\" + c)
  }
  def exact(field: String, q: String) = s"""($field:"${escape(q)}")"""
  def fuzzy(field: String, q: String) = s"""($field:${escape(q)}~)"""
}
