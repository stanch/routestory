package net.routestory

import _root_.android.util.Log
import android.app.Application
import android.content.Context
import android.content.Context._
import android.net.ConnectivityManager
import com.couchbase.cblite._
import com.couchbase.cblite.ektorp.CBLiteHttpClient
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory
import com.couchbase.cblite.support.HttpClientFactory
import net.routestory.model._
import org.apache.http.HttpException
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.protocol.HttpContext
import org.ektorp.android.http.AndroidHttpClient
import org.ektorp._
import org.ektorp.http.HttpClient
import org.ektorp.impl.StdCouchDbInstance

import java.io.InputStream
import scala.reflect.ClassTag
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._
import org.codehaus.jackson.map.ObjectMapper

object StoryApplication {
    val storyPreviewDuration = 30
}

class StoryApplication extends Application {
    var authToken: String = _
    var authorId: String = _

    var cblServer: Option[CBLServer] = None
    var cblInstance: Option[CouchDbInstance] = None

    var localCouch: Option[CouchDbConnector] = None
    var remoteCouch: Option[CouchDbConnector] = None

    CBLURLStreamHandlerFactory.registerSelfIgnoreError()

    private def initLocalCouch() {
        try {
            /* create the server */
            cblServer = Some(new CBLServer(getFilesDir.getAbsolutePath))
            cblServer.get.setDefaultHttpClientFactory(new HttpClientFactory {
                override def getHttpClient: org.apache.http.client.HttpClient = {
                    val httpClient = new DefaultHttpClient
                    val authInterceptor = new HttpRequestInterceptor {
                        override def process(request: HttpRequest, context: HttpContext) {
                            request.addHeader("Authorization", getRemoteAuthToken)
                        }
                    }
                    httpClient.addRequestInterceptor(authInterceptor)
                    httpClient
                }
            })
            val client = cblServer.map(new CBLiteHttpClient(_))
            cblInstance = client.map(new StdCouchDbInstance(_))
            localCouch = cblInstance.map(_.createConnector("story", true))

            /* setup views */
            val db = cblServer.get.getDatabaseNamed("story", true)
            db.getViewNamed("Story/byme").setMapReduceBlocks(new CBLViewMapBlock {
                override def map(document: java.util.Map[String, Object], emitter: CBLViewMapEmitBlock) {
                    if (document.get("type").equals("story") &&
                        (document.get("author") == null || document.get("author").equals(getAuthorId))) {
                        emitter.emit(document.get("starttime"), document.get("_id"))
                    }
                }
            }, null, "1.0")
            db.getViewNamed("Story/saved").setMapReduceBlocks(new CBLViewMapBlock {
                override def map(document: java.util.Map[String, Object], emitter: CBLViewMapEmitBlock) {
                    if (document.get("type").equals("story") &&
                        document.get("author") != null && !document.get("author").equals(getAuthorId)) {
                        emitter.emit(document.get("starttime"), document.get("_id"))
                    }
                }
            }, null, "1.0")
            db.getViewNamed("Story/withComments").setMapReduceBlocks(new CBLViewMapBlock {
                override def map(document: java.util.Map[String, Object], emitter: CBLViewMapEmitBlock) {
                    if (document.get("type").equals("story")) {
                        emitter.emit(ComplexKey.of(document.get("_id"), 0.asInstanceOf[Object]), null)
                    } else if (document.get("type").equals("comment")) {
                        emitter.emit(ComplexKey.of(document.get("story"), 1.asInstanceOf[Object], document.get("timestamp")), null)
                    }
                }
            }, null, "1.0")
        } catch {
            case e: Throwable ⇒ e.printStackTrace()
        }
    }

    private def initRemoteCouch() {
        future {
            val client = new AndroidHttpClient.Builder().connectionTimeout(10000).url("https://bag-routestory-net.herokuapp.com:443/").build()
            synchronized(remoteCouch = Some(new StdCouchDbInstance(client).createConnector("story", false)))
        }
    }

    def setAuthData(s: Array[String]) {
        authorId = s(0)
        authToken = s(1)
        val prefs = getSharedPreferences("default", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("authToken", authToken)
        editor.putString("authorId", authorId)
        editor.commit()
        sync()
    }

    def signOut() {
        setAuthData(Array[String](null, null))
    }

    def isSignedIn = getRemoteAuthToken != null

    def getRemoteAuthToken = {
        if (authToken == null) {
            authToken = getSharedPreferences("default", MODE_PRIVATE).getString("authToken", null)
        }
        authToken
    }

    def getAuthorId = {
        if (authorId == null) {
            authorId = getSharedPreferences("default", MODE_PRIVATE).getString("authorId", null)
        }
        authorId
    }

    def getAuthor = localCouch.map(_.get(classOf[Author], getAuthorId)).getOrElse(null)

    def createStory(story: Story) {
        localCouch.map(_.create(story))
    }

    def updateStoryAttachment(attachment_id: String, contentStream: InputStream, contentType: String, id: String, rev: String) = {
        cblServer.map(_.getDatabaseNamed("story").updateAttachment(attachment_id, contentStream, contentType, id, rev, new CBLStatus).getRevId).getOrElse(null)
    }

    def compactLocal() {
        cblServer.map(_.getDatabaseNamed("story").compact())
    }

    def deleteStory(story: Story) {
        localCouch.map(_.delete(story))
    }

    def localContains(id: String) = {
        localCouch.map(_.contains(id)) getOrElse false
    }

    def remoteContains(id: String) = {
        !localContains(id) || remoteCouch.map(_.contains(id)).getOrElse(false)
    }

    def localOrRemote[A](local: Boolean, f: CouchDbConnector ⇒ A): Future[A] = {
        if (local) Future.successful(
            f(localCouch.get))
        else future {
            f(remoteCouch.get)
        }
    }

    private def couchGet[A <: CouchDbObject: ClassTag](couch: CouchDbConnector, id: String) = {
        val obj = couch.get(implicitly[ClassTag[A]].runtimeClass, id).asInstanceOf[A]
        obj.bind(couch)
        obj
    }

    def getObject[A <: CouchDbObject: ClassTag](id: String): Future[A] = {
        if (id == null) {
            Future.failed(new Exception)
        } else localOrRemote(localContains(id), couchGet[A](_, id))
    }

    def getObjects[A <: CouchDbObject: ClassTag](ids: List[String]): Future[Map[String, A]] = {
        val (local, remote) = ids.filter(_ != null).partition(localContains(_))
        val localObjects = local.map(id ⇒ (id, couchGet[A](localCouch.get, id))).toMap
        if (remote.isEmpty) Future.successful(
            localObjects)
        else future {
            localObjects ++ remote.map(id ⇒ (id, couchGet[A](remoteCouch.get, id))).toMap
        }
    }

    lazy val objectMapper = new ObjectMapper()
    def getQueryResults[A: ClassTag](remote: Boolean, query: ViewQuery, bookmark: Option[String]): Future[(List[A], Int, String)] = {
        bookmark.foreach(query.queryParam("bookmark", _))
        getPlainQueryResults(remote, query) map { results ⇒
            (results.getRows.toList map { row ⇒
                objectMapper.readValue(if (remote) row.getValue else row.getDoc, implicitly[ClassTag[A]].runtimeClass).asInstanceOf[A]
            }, results.getTotalRows, results.getUpdateSeqAsString)
        }
    }

    def getPlainQueryResults(remote: Boolean, query: ViewQuery): Future[ViewResult] = {
        localOrRemote(!remote, _.queryView(query))
    }

    def isOnline = {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
        val netInfo = cm.getActiveNetworkInfo
        (netInfo != null && netInfo.isConnected)
    }

    def sync() {
        if (localCouch.isEmpty) initLocalCouch()
        if (remoteCouch.isEmpty) initRemoteCouch()
        if (isSignedIn && isOnline) {
            val push = new ReplicationCommand.Builder().source("story").target("https://bag-routestory-net.herokuapp.com/story").build()
            cblInstance.map(_.replicate(push))
            val pull = new ReplicationCommand.Builder().target("story").source("https://bag-routestory-net.herokuapp.com/story").build()
            cblInstance.map(_.replicate(pull))
        }
    }
}