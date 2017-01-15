package net.routestory.bag

import akka.actor._
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Future
import spray.can.Http
import spray.http._
import spray.http.HttpHeaders.Accept
import spray.routing._
import spray.client.pipelining._
import scala.util.Properties
import spray.httpx.unmarshalling.Unmarshaller
import play.api.libs.json._
import spray.httpx.marshalling.Marshaller

object Main extends App {
  implicit val system = ActorSystem("routestory")
  val service = system.actorOf(Props[RouteStoryServiceActor], "service")
  IO(Http) ! Http.Bind(service, interface = "0.0.0.0", port = Properties.envOrElse("PORT", "8080").toInt)
}

class RouteStoryServiceActor extends HttpServiceActor with RouteStoryService {
  implicit lazy val actorSystem = context.system
  implicit val ectx = executionContext

  val connector = IO(Http).ask(Http.HostConnectorSetup(host = "routestory.cloudant.com", port = 443, sslEncryption = true)) map {
    case Http.HostConnectorInfo(c: ActorRef, _) ⇒
      addCredentials(BasicHttpCredentials("ioneveredgendartheretted", "yUE3vHifamEEoJrPRHlnw0sj")) ~>
        removeHeader("host") ~>
        addHeader(Accept(MediaTypes.`application/json`)) ~>
        sendReceive(c)
  }

  val couchPipeline = { request: HttpRequest ⇒ connector.flatMap(_(request)) }

  def receive = runRoute(theRoute)
}

trait RouteStoryService extends HttpService
  with SiteRoutes
  with AuthRoutes
  with StoryRoutes
  with AuthorRoutes
  with TagRoutes {

  implicit def actorSystem: ActorSystem
  implicit val executionContext = actorRefFactory.dispatcher
  implicit val timeout: Timeout = 10.seconds

  /* Pipelines */
  implicit def jsValueUnmarshaller = Unmarshaller.delegate[String, JsValue](MediaTypes.`application/json`)(Json.parse)
  implicit def jsValueMarshaller = Marshaller.delegate[JsValue, String](MediaTypes.`application/json`)(Json.stringify _)
  def couchPipeline: SendReceive
  lazy val couchJsonPipeline = couchPipeline ~> unmarshal[JsValue]
  def couchComplete(request: HttpRequest) = complete(couchPipeline(request))

  val theRoute =
    pathPrefix("auth") {
      authRoutes
    } ~
    pathPrefix("api") {
      storyRoutes ~ authorRoutes ~ tagRoutes
    } ~
    siteRoutes
}
