package net.routestory.lounge

import scala.language.dynamics
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory
import com.couchbase.cblite.{ CBLStatus, CBLViewMapEmitBlock, CBLViewMapBlock, CBLServer }
import com.couchbase.cblite.support.HttpClientFactory
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.{ HttpRequest, HttpRequestInterceptor }
import org.apache.http.protocol.HttpContext
import com.couchbase.cblite.ektorp.CBLiteHttpClient
import org.ektorp.impl.StdCouchDbInstance
import org.ektorp.ComplexKey
import android.content.Context
import scala.concurrent.{ Future, future }
import scala.concurrent.ExecutionContext.Implicits.global
import rx._
import java.io.InputStream
import scala.collection.JavaConverters._

trait LocalCouch {
  CBLURLStreamHandlerFactory.registerSelfIgnoreError()
  implicit val ctx: Context

  val authToken: Var[Option[String]]
  val authorId: Var[Option[String]]

  object Local {
    val server = future(new CBLServer(ctx.getFilesDir.getAbsolutePath))
    val client = server.map(new CBLiteHttpClient(_))
    val instance = client.map(new StdCouchDbInstance(_))
    val couch = instance.map(_.createConnector("story", true))

    Obs(authToken) {
      setHttpFactory(server, authToken())
    }

    Obs(authorId) {
      setViews(server, authorId())
    }

    def updateAttachment(attachmentId: String, contentStream: InputStream, contentType: String, id: String, rev: String) = {
      server.map(_.getDatabaseNamed("story").updateAttachment(attachmentId, contentStream, contentType, id, rev, new CBLStatus).getRevId)
    }

    def compact() {
      server.foreach(_.getDatabaseNamed("story").compact())
    }
  }

  private def setHttpFactory(server: Future[CBLServer], token: Option[String]) {
    server.foreach { s ⇒
      s.setDefaultHttpClientFactory(new HttpClientFactory {
        override def getHttpClient = new DefaultHttpClient {
          addRequestInterceptor(new HttpRequestInterceptor {
            override def process(request: HttpRequest, context: HttpContext) {
              request.addHeader("Authorization", token.getOrElse(""))
            }
          })
        }
      })
    }
  }

  private def setViews(server: Future[CBLServer], author: Option[String]) {
    server.map(_.getDatabaseNamed("story", true)).map { db ⇒
      implicit class Document(doc: java.util.Map[String, Object]) extends scala.Dynamic {
        def selectDynamic(sel: String) = doc.get(sel)
      }
      def setView(name: String)(map: (Document, CBLViewMapEmitBlock) ⇒ Unit) = {
        db.getViewNamed(s"Story/$name").setMapReduceBlocks(new CBLViewMapBlock {
          override def map(d: java.util.Map[String, Object], e: CBLViewMapEmitBlock) { map(d, e) }
        }, null, "1.0")
      }
      def emitDoc(doc: Document, emitter: CBLViewMapEmitBlock) = emitter.emit(doc.starttime, Map(
        "_id" → doc._id,
        "title" → doc.title,
        "tags" → doc.tags,
        "author" → doc.author,
        "locations" → doc.locations
      ).asJava)
      setView("byme") { (doc, emitter) ⇒
        if (doc.`type` == "story" && (doc.author == null || doc.author == author)) emitDoc(doc, emitter)
      }
      setView("saved") { (doc, emitter) ⇒
        if (doc.`type` == "story" && doc.author != null && doc.author != author) emitDoc(doc, emitter)
      }
    }
  }
}
