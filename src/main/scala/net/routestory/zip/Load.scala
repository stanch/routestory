package net.routestory.zip

import java.io.File

import net.routestory.data.Author
import net.routestory.json.JsonRules
import resolvable.Resolvable

object Load {
  def apply(input: File) = {
    object rules extends JsonRules {
      override def author(id: String) = Resolvable.resolved(Author(id, "Doctor Who", None, None))
      override def media(url: String): Resolvable[File] = ???
    }


  }
}
