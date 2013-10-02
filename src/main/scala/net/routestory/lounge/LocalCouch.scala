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
import android.content.Context
import scala.concurrent.{ Future, future }
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.InputStream
import android.util.Log

trait LocalCouch {
  CBLURLStreamHandlerFactory.registerSelfIgnoreError()
  implicit val ctx: Context

  object Local {
    val server = future(new CBLServer(ctx.getFilesDir.getAbsolutePath))
    val client = server.map(new CBLiteHttpClient(_))
    val instance = client.map(new StdCouchDbInstance(_))
    val couch = instance.map(_.createConnector("story", true))

    def updateAttachment(attachmentId: String, contentStream: InputStream, contentType: String, id: String, rev: String) = {
      server.map(_.getDatabaseNamed("story").updateAttachment(attachmentId, contentStream, contentType, id, rev, new CBLStatus).getRevId)
    }

    def compact() {
      server.foreach(_.getDatabaseNamed("story").compact())
    }
  }

  protected def setHttpFactory(server: Future[CBLServer], token: Option[String]) = {
    server.map { s ⇒
      s.setDefaultHttpClientFactory(new HttpClientFactory {
        override def getHttpClient = new DefaultHttpClient {
          addRequestInterceptor(new HttpRequestInterceptor {
            override def process(request: HttpRequest, context: HttpContext) {
              request.addHeader("Authorization", token.getOrElse(""))
            }
          })
        }
      })
      Log.d("Sync", s"Http factory set to $token")
    }
  }

  protected def setViews(server: Future[CBLServer], author: Option[String]) = {
    server.map(_.getDatabaseNamed("story", true)).map { db ⇒
      // setMapReduceBlocks doesn’t work with closures!
      // perhaps some weird reflection stuff
      db.getViewNamed("Story/byme").setMapReduceBlocks(new CBLViewMapBlock {
        override def map(document: java.util.Map[String, Object], emitter: CBLViewMapEmitBlock) {
          if (document.get("type") == "story" &&
            (document.get("author") == null || document.get("author") == author.getOrElse(""))) {
            emitter.emit(document.get("starttime"), document.get("_id"))
          }
        }
      }, null, "1.0")
      Log.d("Sync", s"Views set for author $author")
    }
  }
}
