package net.routestory.zip

import java.io._
import java.util.zip.{ZipEntry, ZipOutputStream}

import net.routestory.data._
import net.routestory.json.JsonWrites._
import org.apache.commons.io.IOUtils
import play.api.data.mapping.To
import play.api.libs.json.JsObject

import scala.async.Async._
import scala.concurrent.ExecutionContext

object Save {
  def apply(story: Story, output: File)(implicit ec: ExecutionContext) = async {
    // prepare zip file
    val out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))

    // store media files
    val (renamed, files) = await(Utils.withJustFilenames(story))
    files foreach {
      case (f, n) ⇒ addFile(out, f, n)
    }

    // convert to JSON
    val json = To[Story, JsObject](renamed)
    val jsonFile = File.createTempFile("story", ".json")
    IOUtils.write(json.toString(), new FileOutputStream(jsonFile), "UTF-8")
    addFile(out, jsonFile, "story.json")
    jsonFile.delete()

    // finish
    out.close()
  }

  def addFile(out: ZipOutputStream, file: File, name: String) = {
    val stream = new BufferedInputStream(new FileInputStream(file))
    val entry = new ZipEntry(name)
    out.putNextEntry(entry)
    IOUtils.copy(stream, out)
    stream.close()
  }
}
