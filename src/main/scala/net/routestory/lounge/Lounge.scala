package net.routestory.lounge

import scala.async.Async.{ async, await }
import org.ektorp.{ ReplicationCommand, ViewResult, ViewQuery, CouchDbConnector }
import net.routestory.model.CouchDbObject
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global
import org.codehaus.jackson.map.ObjectMapper
import scala.collection.JavaConversions._
import scala.concurrent.{ Promise, Future, future }
import java.util.{ Observer, Observable }
import com.couchbase.cblite.replicator.CBLReplicator
import scala.util.Success
import android.util.Log
import rx.Var
import java.util.concurrent.{ ScheduledExecutorService, TimeUnit, Executors }

trait Lounge extends LocalCouch with RemoteCouch {
  val authToken: Var[Option[String]]
  val authorId: Var[Option[String]]
  def isSignedIn = authToken.now.isDefined

  private var syncService: Option[ScheduledExecutorService] = None

  /** Setup http client and views */
  def init = async {
    await(setHttpFactory(Local.server, authToken.now))
    await(setViews(Local.server, authorId.now))
    import org.scaloid.common._
    // syncService is only instantiated at this point
    // to make sure we can’t replicate before authToken and authorId are defined
    syncService = Some(Executors.newScheduledThreadPool(1))
    syncService.foreach(_.scheduleAtFixedRate(sync, 5, 5 * 60, TimeUnit.SECONDS))
  }

  /** Set up http client to use authentication */
  def signIn(token: Option[String], id: Option[String]) = async {
    await(setHttpFactory(Local.server, token))
    await(setViews(Local.server, id))
    authToken.update(token)
    authorId.update(id)
    await(requestSync)
  }

  /** Clear authentication token */
  def signOut = signIn(None, None)

  /** Check if local couch contains a document */
  def localContains(id: String) = {
    Local.couch.map(_.contains(id))
  }

  /** Check if remote couch contains a document */
  def remoteContains(id: String) = async {
    if (!await(localContains(id))) true
    else await(Remote.couch.map(_.contains(id)))
  }

  /** Perform an action on either local or remote couch */
  private def localOrRemote[A](local: Boolean, f: CouchDbConnector ⇒ A) = (if (local) Local.couch else Remote.couch).map(f)

  /** Get document by id */
  private def couchGet[A <: CouchDbObject: ClassTag](couch: CouchDbConnector, id: String) = {
    val obj = couch.get(implicitly[ClassTag[A]].runtimeClass, id).asInstanceOf[A]
    obj.bind(couch); obj
  }

  /** Get document by id */
  def getObject[A <: CouchDbObject: ClassTag](id: String): Future[A] = if (id == null) {
    Future.failed(new Exception)
  } else {
    localContains(id).flatMap(c ⇒ localOrRemote(c, couchGet[A](_, id)))
  }

  /** Get several documents by ids */
  def getObjects[A <: CouchDbObject: ClassTag](ids: List[String]): Future[Map[String, A]] = {
    // TODO: partition into local and remote and use bulk gets!
    Future.sequence(ids.filter(_ != null).map(id ⇒ getObject[A](id).map(x ⇒ (id, x)))).map(_.toMap)
  }

  /** Get query results as Ektorp rows */
  def getPlainQueryResults(remote: Boolean, query: ViewQuery): Future[ViewResult] = {
    localOrRemote(!remote, _.queryView(query))
  }

  lazy val objectMapper = new ObjectMapper
  /** Get query results as documents */
  def getQueryResults[A: ClassTag](remote: Boolean, query: ViewQuery, bookmark: Option[String]): Future[(List[A], Int, String)] = {
    bookmark.foreach(query.queryParam("bookmark", _))
    getPlainQueryResults(remote, query) map { results ⇒
      (results.getRows.toList.map { row ⇒
        objectMapper.readValue(if (remote) row.getValue else row.getDoc, implicitly[ClassTag[A]].runtimeClass).asInstanceOf[A]
      }, results.getTotalRows, results.getUpdateSeqAsString)
    }
  }

  /** Observe an observable until a predicate is true */
  private def observeUntil(observable: Observable)(predicate: (Observable, Object) ⇒ Boolean) = {
    val promise = Promise[Unit]()
    observable.addObserver(new Observer {
      override def update(o: Observable, data: Object) {
        if (predicate(o, data)) {
          observable.deleteObserver(this)
          promise.complete(Success(()))
        }
      }
    })
    promise.future
  }

  /** Replicate */
  private def replicate(c: ReplicationCommand) = Future.firstCompletedOf(Seq(async {
    val status = await(Local.instance.map(_.replicate(c)))
    val replicator = await(Local.server.map(_.getDatabaseNamed("story").getReplicator(status.getSessionId)))
    await(observeUntil(replicator) { (o, d) ⇒ !o.asInstanceOf[CBLReplicator].isRunning })
  }, future {
    // TODO: use proper API once it’s ready
    Thread.sleep(5000)
  }))

  /** Synchronize with the cloud */
  private def sync = if (isSignedIn) async {
    Log.d("Sync", "Replicating")
    val push = new ReplicationCommand.Builder().source("story").target("https://bag-routestory-net.herokuapp.com/story").build()
    await(replicate(push))
    Log.d("Sync", "Finished pushing")
    val pull = new ReplicationCommand.Builder().target("story").source("https://bag-routestory-net.herokuapp.com/story").build()
    await(replicate(pull))
    Log.d("Sync", "Finished pulling")
  } recover {
    case t ⇒ t.printStackTrace()
  }
  else Future.successful(())

  /** This should be the only entry point for replication (for thread safety) */
  def requestSync = {
    import org.scaloid.common._
    val syncPromise = Promise[Any]()
    syncService.map(_.schedule({
      syncPromise.completeWith(sync)
    }, 0, TimeUnit.SECONDS)).getOrElse(syncPromise.complete(Success(())))
    syncPromise.future
  }
}
