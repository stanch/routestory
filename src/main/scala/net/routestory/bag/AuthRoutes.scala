package net.routestory.bag

import spray.client.pipelining._
import spray.http._
import scala.async.Async.{async, await}
import spray.http.Uri.Query
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorRefFactory

trait Author {
  val originalId: String
  val name: String
  val link: Option[String]
  val picture: Option[String]
}

object Google {
  case class User(id: String, name: String, link: Option[String], picture: Option[String]) extends Author {
    val originalId = "google-" + id
  }
  object User {
    implicit val format = Json.format[User]
  }

  // TODO: config?
  private val mobileApiKey = "931963850534-p4jvm80ebfg5l31rgdatfg82gbarc244.apps.googleusercontent.com"

  private def tokenValidationUri(token: String) = Uri.from(
    scheme = "https", host = "www.googleapis.com", path = "/oauth2/v1/tokeninfo",
    query = Query("access_token" → token)
  )

  private def userInfoUri(token: String) = Uri.from(
    scheme = "https", host = "www.googleapis.com", path = "/oauth2/v1/userinfo",
    query = Query("access_token" → token)
  )

  def fromToken(token: String, pipeline: HttpRequest ⇒ Future[JsValue])(implicit ec: ExecutionContext): Future[Author] = async {
    // validate access token
    val valid = await(pipeline(Get(tokenValidationUri(token))))
    assert((valid \ "error").isInstanceOf[JsUndefined])
    assert((valid \ "audience") == JsString(mobileApiKey))

    // get user info from Google
    await(pipeline(Get(userInfoUri(token)))).as[User]
  }
}

trait AuthRoutes { self: RouteStoryService ⇒
  private def updateAuthor(author: Author) = async {
    import Json._

    // check if the author is in the database
    val docs = await(couchJsonPipeline {
      Couch.viewReq("Author", "byId", "key" → toJson(author.originalId).toString)
    })

    // get author id and info
    val (id, doc) = docs \ "rows" match {
      case JsArray(Seq(user)) ⇒
        val i = (user \ "id").as[String]
        val doc = await(couchJsonPipeline(Get(Couch.docUri(i))))
        (i, doc.as[JsObject])
      case _ ⇒
        (Shortuuid.make, obj())
    }

    // update/create info
    await(couchPipeline(Put(Couch.docUri(id), doc ++ obj(
      "_id" → toJson(id),
      "type" → "author",
      "originalId" → toJson(author.originalId),
      "name" → toJson(author.name),
      "link" → toJson(author.link),
      "picture" → toJson(author.picture)
    ))))

    obj("authorId" → toJson(id), "authToken" → toJson(Cookery.encode(id)))
  }

  val authRoutes =
    (path("android") & get & parameter('token)) { token ⇒
      complete(async {
        val author = await(Google.fromToken(token, sendReceive ~> unmarshal[JsValue]))
        await(updateAuthor(author))
      } recover {
        case t ⇒ t.printStackTrace(); Json.obj("error" → Json.toJson(true))
      })
    }
}
