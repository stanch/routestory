import akka.actor._
import akka.dataflow._
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.can.{ HostConnectorInfo, Http, HostConnectorSetup }
import spray.http._
import spray.http.HttpHeaders.Accept
import spray.routing._
import spray.client.pipelining._
import spray.json._
import DefaultJsonProtocol._
import org.mutate.Mutate._

object Main extends App {
  implicit val system = ActorSystem("routestory")
  val service = system.actorOf(Props[RouteStoryServiceActor], "service")
  IO(Http) ! Http.Bind(service, "localhost", port = 8080)
}

class RouteStoryServiceActor extends Actor with RouteStoryService {
  import spray.httpx.{ ResponseTransformation, RequestBuilding }

  implicit val actorSystem = context.system
  implicit val ectx = context.dispatcher
  def actorRefFactory = context

  val proxyPipeline = IO(Http).ask(HostConnectorSetup(host = "routestory.cloudant.com", port = 443, sslEncryption = true)) map {
    case HostConnectorInfo(c, _) ⇒
      addCredentials(BasicHttpCredentials("ioneveredgendartheretted", "yUE3vHifamEEoJrPRHlnw0sj")) ~>
        { req: HttpRequest ⇒ mutate(req) { $ ⇒ $.headers ~= (_.filterNot(_.is("host"))) } } ~>
        addHeader(Accept(MediaTypes.`application/json`)) ~>
        RequestBuilding.logRequest { x: HttpRequest ⇒ println(x) } ~>
        sendReceive(c.asInstanceOf[ActorRef]) ~>
        ResponseTransformation.logResponse { x: HttpResponse ⇒ println(x) }
  }

  def receive = runRoute(theRoute)
}

trait RouteStoryService extends HttpService {
  import Filters._
  import spray.httpx.SprayJsonSupport._
  implicit val executionContext = actorRefFactory.dispatcher
  implicit val timeout: Timeout = 10.seconds

  val proxyPipeline: Future[HttpRequest ⇒ Future[HttpResponse]]

  /* Http proxy helpers */
  def proxySend(req: HttpRequest): Future[HttpResponse] = proxyPipeline.flatMap(_(req))
  def proxyPass(req: HttpRequest) = complete(proxySend(req))
  def proxyPassUnmodified = extract(_.request)(proxyPass)

  /* Stories. TODO */
  val storyRoute = path("""story-[A-Za-z0-9]+""".r) { id ⇒
    (get | head) {
      proxyPassUnmodified
    } ~
      post {
        ???
      }
  }

  /* Authors. Only GET method allowed; all sensitive information is removed */
  val authorRoute = (path("""author-[A-Za-z0-9]+""".r) & get & extract(_.request)) { (_, req) ⇒
    complete(flow {
      val response = proxySend(req).apply()
      response.mapEntityJson(_.filterKeys(Set("_id", "_rev", "_revisions", "_deleted", "name", "picture", "link", "type")))
    })
  }

  // GET document
  val docRoute = storyRoute ~ authorRoute

  // CouchDB changes feed
  val changesRoute = (path("_changes") & get) {
    complete(???)
  }

  // Download multiple documents
  val alldocsRoute = (path("_all_docs") & post) {
    complete(???)
  }

  // Upload multiple documents
  val bulkdocsRoute = (path("_bulk_docs") & post) {
    complete(???)
  }

  // Fetch revision differences
  val revsdiffRoute = (path("_revs_diff") & post) {
    proxyPassUnmodified
  }

  // GET and PUT checkpoints
  val checkpointRoute = pathPrefix("_local") {
    get {
      proxyPassUnmodified
    } ~
      (put & extract(_.request)) { req ⇒
        val request = req.mapEntityJson(_.filterKeys(Set("_id", "_rev", "lastSequence")))
        proxyPass(request)
      }
  }

  // Lucene search adapter
  val luceneRoute = (path("_design" / "Story" / "_view" / "textQuery") & get & parameter('q) & extract(_.request)) { (q, req: HttpRequest) ⇒
    filterParameters(Set("q", "skip", "limit")) {
      complete(flow {
        // TODO self.strip_arguments(['skip', 'limit'])
        // TODO URI lens
        val request = mutate(req) { $ ⇒ $.uri.path := Uri.Path("/story/_design/Story/_search/byEverything") }
        val response = proxySend(request).apply()

        import spray.json.lenses.JsonLenses._
        response.mapEntityJson { j ⇒
          j.filterKeys(Set("total_rows", "rows")).update('rows / * ! modify { row: JsValue ⇒
            JsObject(
              "id" → row.extract[JsValue]('id),
              "key" → row.extract[JsValue]('order),
              "value" → row.extract[JsValue]('fields).update(
                field("_id") ! set {
                  row.extract[String]('id)
                }
              ).update(
                  field("tags") ! modify { t: JsValue ⇒
                    t match {
                      case xs @ JsArray(_) ⇒ xs
                      case x ⇒ JsArray(x).asInstanceOf[JsValue]
                    }
                  }
                )
            )
          })
        }
      })
    }
  }

  // View Queries
  val queryviewRoute = (path("_design" / "Story" / "_view" / Rest) & get & extract(_.request)) { (viewName, req) ⇒
    filterParameters(Set("offset", "limit", "reduce", "group", "key", "startkey", "end", "descending")) {
      complete(flow {
        import spray.json.lenses.JsonLenses._
        val response = proxySend(req).apply()
        response.mapEntityJson { j ⇒
          j.update('rows / * ! modify { row: JsValue ⇒
            row.update('value / 'id ! set {
              row.extract[String]('id)
            })
          })
        }
      })
    }
  }

  // HEAD db
  val dbRoute = (path("") & head) {
    proxyPassUnmodified
  }

  val theRoute = pathPrefix("story") {
    docRoute ~
      changesRoute ~ alldocsRoute ~
      revsdiffRoute ~ bulkdocsRoute ~
      checkpointRoute ~
      luceneRoute ~ queryviewRoute ~
      dbRoute
  }
}