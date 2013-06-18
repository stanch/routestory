package net.routestory

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

object StoryApplication {
    val storyPreviewDuration = 30
}

class StoryApplication extends Application {
    var mCBLServer: CBLServer = _
    var mLocalHttpClient: HttpClient = _
    var mLocalCouchDbInstance: CouchDbInstance = _
    var mLocalCouchDbConnector: CouchDbConnector = _

    var mRemoteAuthToken: String = _
    var mAuthorId: String = _
    var mRemoteHttpClient: HttpClient = _
    var mRemoteCouchDbInstance: CouchDbInstance = _
    var mRemoteCouchDbConnector: CouchDbConnector = _

    CBLURLStreamHandlerFactory.registerSelfIgnoreError()

    override def onCreate() {
        //initLocalCouch()
        //initRemoteCouch()
    }

    private def initLocalCouch() {
        try {
            val filesDir = getFilesDir.getAbsolutePath
            mCBLServer = new CBLServer(filesDir)
            mCBLServer.setDefaultHttpClientFactory(new HttpClientFactory {
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
            mLocalHttpClient = new CBLiteHttpClient(mCBLServer)
            mLocalCouchDbInstance = new StdCouchDbInstance(mLocalHttpClient)
            mLocalCouchDbConnector = mLocalCouchDbInstance.createConnector("story", true)
            val db = mCBLServer.getDatabaseNamed("story", true)
            db.getViewNamed("Story/byme").setMapReduceBlocks(new CBLViewMapBlock() {
                override def map(document: java.util.Map[String, Object], emitter: CBLViewMapEmitBlock) {
                    if (document.get("type").equals("story") &&
                        (document.get("author")==null || document.get("author").equals(getAuthorId))) {
                        emitter.emit(document.get("starttime"), document.get("_id"))
                    }
                }
            }, null, "1.0")
            db.getViewNamed("Story/saved").setMapReduceBlocks(new CBLViewMapBlock() {
                override def map(document: java.util.Map[String, Object], emitter: CBLViewMapEmitBlock) {
                    if (document.get("type").equals("story") &&
                        document.get("author")!=null && !document.get("author").equals(getAuthorId)) {
                        emitter.emit(document.get("starttime"), document.get("_id"))
                    }
                }
            }, null, "1.0")
            db.getViewNamed("Story/withComments").setMapReduceBlocks(new CBLViewMapBlock() {
                override def map(document: java.util.Map[String, Object], emitter: CBLViewMapEmitBlock) {
                    if (document.get("type").equals("story")) {
                        emitter.emit(ComplexKey.of(document.get("_id"), 0.asInstanceOf[Object]), null)
                    } else if (document.get("type").equals("comment")) {
                        emitter.emit(ComplexKey.of(document.get("story"), 1.asInstanceOf[Object], document.get("timestamp")),  null)
                    }
                }
            }, null, "1.0")
        } catch {
            case e: Throwable ⇒ e.printStackTrace()
        }
    }

    private def initRemoteCouch() {
        future {
            mRemoteHttpClient = new AndroidHttpClient.Builder().url("http://couch.story.stanch.me:80/").build()
            mRemoteCouchDbInstance = new StdCouchDbInstance(mRemoteHttpClient)
            mRemoteCouchDbConnector = mRemoteCouchDbInstance.createConnector("story", false)
        }
    }

    def setAuthData(s: Array[String]) {
        mAuthorId = s(0)
        mRemoteAuthToken = s(1)
        val prefs = getSharedPreferences("default", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("remoteAuthToken", mRemoteAuthToken)
        editor.putString("authorId", mAuthorId)
        editor.commit()
        sync()
    }

    def signOut() {
        setAuthData(Array[String](null, null))
    }

    def isSignedIn = getRemoteAuthToken != null

    def getRemoteAuthToken = {
        if (mRemoteAuthToken == null) {
            mRemoteAuthToken = getSharedPreferences("default", MODE_PRIVATE).getString("remoteAuthToken", null)
        }
        mRemoteAuthToken
    }
    def getAuthorId = {
        if (mAuthorId == null) {
            mAuthorId = getSharedPreferences("default", MODE_PRIVATE).getString("authorId", null)
        }
        mAuthorId
    }
    def getAuthor = {
        if (mAuthorId == null) {
            mAuthorId = getSharedPreferences("default", MODE_PRIVATE).getString("authorId", null)
        }
        mLocalCouchDbConnector.get(classOf[Author], mAuthorId)
    }

    def createStory(story: Story) {
        mLocalCouchDbConnector.create(story)
    }

    def updateStoryAttachment(attachment_id: String, contentStream: InputStream, contentType: String, id: String, rev: String) = {
        mCBLServer.getDatabaseNamed("story").updateAttachment(attachment_id, contentStream, contentType, id, rev, new CBLStatus).getRevId
    }

    def compactLocal() {
        mCBLServer.getDatabaseNamed("story").compact()
    }

    def deleteStory(story: Story) {
        mLocalCouchDbConnector.delete(story)
    }

    def localContains(id: String) = {
        mLocalCouchDbConnector.contains(id)
    }

    def remoteContains(id: String) = {
        !localContains(id) || mRemoteCouchDbConnector.contains(id)
    }

    def localOrRemote[A](local: Boolean, f: CouchDbConnector ⇒ A): Future[A] = {
        if (local) Future.successful(
            f(mLocalCouchDbConnector)
        ) else future {
            f(mRemoteCouchDbConnector)
        }
    }

    private def couchGet[A <: CouchDbObject](couch: CouchDbConnector, id: String)(implicit tag: ClassTag[A]) = {
        val obj = couch.get[A](tag.runtimeClass.asInstanceOf[Class[A]], id)
        obj.bind(couch)
        obj
    }

    def getObject[A <: CouchDbObject](id: String)(implicit tag: ClassTag[A]): Future[A] = {
        if (id == null) {
            Future.failed(new Exception)
        } else localOrRemote(localContains(id), couchGet[A](_, id))
    }

    def getObjects[A <: CouchDbObject](ids: List[String])(implicit tag: ClassTag[A]): Future[Map[String, A]] = {
        val (local, remote) = ids.filter(_ != null).partition(localContains(_))
        val localObjects = local.map(id ⇒ (id, couchGet[A](mLocalCouchDbConnector, id))).toMap
        if (remote.isEmpty) Future.successful(
            localObjects
        ) else future {
            localObjects ++ remote.map(id ⇒ (id, couchGet[A](mRemoteCouchDbConnector, id))).toMap
        }
    }

    def getQueryResults[A](remote: Boolean, query: ViewQuery)(implicit tag: ClassTag[A]): Future[List[A]] = {
        localOrRemote(!remote, _.queryView(query, tag.runtimeClass.asInstanceOf[Class[A]]).toList)
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
		if (false && isSignedIn && isOnline) {
            val push = new ReplicationCommand.Builder().source("story").target("http://couch.story.stanch.me/story").build()
            mLocalCouchDbInstance.replicate(push)
            val pull = new ReplicationCommand.Builder().target("story").source("http://couch.story.stanch.me/story").build()
            mLocalCouchDbInstance.replicate(pull)
        }
	}

	override def onTerminate() {
		if (mLocalHttpClient != null) {
            mLocalHttpClient.shutdown()
            mLocalHttpClient = null
        }
        if (mCBLServer != null) {
            mCBLServer.close()
            mCBLServer = null
        }
        if (mRemoteHttpClient != null) {
            mRemoteHttpClient.shutdown()
            mRemoteHttpClient = null
        }
	}
}