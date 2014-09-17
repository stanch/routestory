package net.routestory.zip

import java.io.{BufferedOutputStream, File, FileOutputStream, InputStream}
import java.util.concurrent.Executors
import java.util.zip.ZipFile

import net.routestory.data.{Author, Story, Timed}
import net.routestory.json.JsonRules
import org.apache.commons.io.IOUtils
import play.api.libs.json.{JsValue, Json}
import resolvable.file.FileEndpoint
import resolvable.json.JsonEndpoint
import resolvable.{Endpoint, EndpointLogger, Resolvable, Source}

import scala.concurrent.{ExecutionContext, Future}

case class Api(archive: ZipFile, mediaPath: File)
  extends Endpoints
  with Needs
  with JsonRules

trait Endpoints {
  def archive: ZipFile
  def mediaPath: File

  trait ZipEndpoint extends Endpoint {
    def entry: String
    def data: InputStream ⇒ Data
    def fetch(implicit ec: ExecutionContext) = Future.successful {
      val stream = archive.getInputStream(archive.getEntry(entry))
      data(stream)
    }
    val logger = EndpointLogger.none
  }

  case class ZipStory() extends JsonEndpoint with ZipEndpoint {
    val entry = "story.json"
    val data: InputStream ⇒ JsValue = stream ⇒ Json.parse(IOUtils.toString(stream))
  }

  case class ZipMedia(url: String) extends FileEndpoint with ZipEndpoint {
    def create = new File(mediaPath.getAbsolutePath + "/" + url)
    val entry = url
    def data: InputStream ⇒ File = { stream ⇒
      IOUtils.copy(stream, new BufferedOutputStream(new FileOutputStream(create)))
      create
    }
  }
}

trait Needs { self: Endpoints with JsonRules ⇒
  def author(id: String) = Resolvable.resolved(Author(id, "", None, None))
  def media(url: String) = Source[File].from(ZipMedia(url))
  def story = Source[Story].from(ZipStory())
}

object Load {
  // force synchronous processing
  implicit lazy val ctx = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

  def apply(input: File, unpackTo: File) = {
    val archive = new ZipFile(input)
    val api = Api(archive, unpackTo)
    api.story map { s ⇒
      val media = s.chapters.toVector flatMap { c ⇒
        c.elements flatMap {
          case Timed(_, m: Story.MediaElement) ⇒ Vector(m.data)
          case _ ⇒ Vector.empty
        }
      }
      Future.sequence(media).foreach(_ ⇒ archive.close())
      s
    }
  }
}
