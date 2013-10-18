package net.routestory.bag

import akka.actor._
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import spray.can.Http
import spray.http._
import spray.http.HttpHeaders.Accept
import spray.routing._
import spray.client.pipelining._
import scala.async.Async.{async, await}
import spray.http.Uri.Query
import scala.util.Properties

object Main extends App {
  implicit val system = ActorSystem("routestory")
  val service = system.actorOf(Props[RouteStoryServiceActor], "service")
  IO(Http) ! Http.Bind(service, interface = "0.0.0.0", port = Properties.envOrElse("PORT", "8080").toInt)
}

class RouteStoryServiceActor extends Actor with RouteStoryService {
  import spray.httpx.{ ResponseTransformation, RequestBuilding }

  implicit val actorSystem = context.system
  implicit val ectx = context.dispatcher
  def actorRefFactory = context

  val proxyPipeline = IO(Http).ask(Http.HostConnectorSetup(host = "routestory.cloudant.com", port = 443, sslEncryption = true)) map {
    case Http.HostConnectorInfo(c: ActorRef, _) ⇒
      addCredentials(BasicHttpCredentials("ioneveredgendartheretted", "yUE3vHifamEEoJrPRHlnw0sj")) ~>
        removeHeader("host") ~>
        addHeader(Accept(MediaTypes.`application/json`)) ~>
        RequestBuilding.logRequest { x: HttpRequest ⇒ println(x) } ~>
        sendReceive(c) ~>
        ResponseTransformation.logResponse { x: HttpResponse ⇒ println(x) }
  }

  def receive = runRoute(theRoute)
}

trait RouteStoryService extends HttpService with SyncRoutes with StoryRoutes {
  implicit val executionContext = actorRefFactory.dispatcher
  implicit val timeout: Timeout = 10.seconds

  val proxyPipeline: Future[HttpRequest ⇒ Future[HttpResponse]]

  /* Http proxy helpers */
  def proxySend(req: HttpRequest): Future[HttpResponse] = proxyPipeline.flatMap(_(req))
  def proxyPass(req: HttpRequest) = complete(proxySend(req))

  val theRoute =
    pathPrefix("sync") {
      syncRoutes
    } ~
    pathPrefix("api") {
      storyRoutes
    }
}