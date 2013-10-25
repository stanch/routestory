package net.routestory.bag

import org.scalatest.FlatSpec
import spray.testkit.ScalatestRouteTest
import akka.io.IO
import spray.can.Http
import akka.actor.ActorRef
import akka.pattern.ask
import spray.client.pipelining._
import spray.http.{HttpRequest, StatusCodes, MediaTypes, BasicHttpCredentials}
import spray.http.HttpHeaders.Accept
import org.scalatest.matchers.ShouldMatchers
import akka.util.Timeout
import play.api.libs.json.JsValue

class RouteStoryServiceSpec extends FlatSpec with ScalatestRouteTest with RouteStoryService with ShouldMatchers {
  def actorRefFactory = system
  override implicit val executionContext = system.dispatcher

  val connector = IO(Http).ask(Http.HostConnectorSetup(host = "routestory.cloudant.com", port = 443, sslEncryption = true)) map {
    case Http.HostConnectorInfo(c: ActorRef, _) ⇒
      addCredentials(BasicHttpCredentials("ioneveredgendartheretted", "yUE3vHifamEEoJrPRHlnw0sj")) ~>
      removeHeader("host") ~>
      addHeader(Accept(MediaTypes.`application/json`)) ~>
      sendReceive(c)
  }

  val couchPipeline = { request: HttpRequest ⇒ connector.flatMap(_(request)) }

  it should "redirect HEAD requests on database" in {
    Head("/sync") ~> theRoute ~> check {
      status should equal (StatusCodes.OK)
    }
  }

  it should "return latest stories" in {
    Get("/api/stories/latest?skip=1") ~> theRoute ~> check {
      status should equal (StatusCodes.OK)
    }
  }

  it should "search by tag" in {
    Get("/api/stories/tags/lisbon") ~> theRoute ~> check {
      status should equal (StatusCodes.OK)
    }
  }

  it should "search by tag and title" in {
    Get("/api/stories/search/lisbo%20n") ~> theRoute ~> check {
      status should equal (StatusCodes.OK)
    }
  }

  it should "allow to get stories" in {
    Get("/api/stories/story-Yt5toJcTsyu87ehpgqzqE5") ~> theRoute ~> check {
      status should equal (StatusCodes.OK)
    }
  }

//  it should "allow to get story attachments" in {
//    Get("/api/stories/story-Yt5toJcTsyu87ehpgqzqE5/images/1.jpg") ~> theRoute ~> check {
//      status should equal (StatusCodes.OK)
//    }
//  }

  it should "provide a list of all tags" in {
    Get("/api/tags") ~> theRoute ~> check {
      status should equal (StatusCodes.OK)
    }
  }

  it should "allow to get authors" in {
    Get("/api/authors/author-27u6ExbZKej8ML9QcnYCf3") ~> theRoute ~> check {
      status should equal (StatusCodes.OK)
    }
  }

  it should "handle android auth" in {
    Get("/auth/android?token=ya29.AHES6ZStuaz7Bsto7SGv-B6muc5OtgacXi7Bo6T-QDj3GkADNzk") ~> theRoute ~> check {
      println(response)
    }
  }
}
