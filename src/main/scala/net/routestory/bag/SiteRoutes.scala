package net.routestory.bag

import com.joypeg.scamandrill.client.MandrillAsyncClient
import com.joypeg.scamandrill.models.{MTo, MSendMsg, MSendMessage}
import redis.RedisClient
import scala.async.Async._
import scala.util.Random

trait SiteRoutes { self: RouteStoryService ⇒
  def msg(text: String, topic: String) = MSendMessage(
    "Saw-MObLFMXL4hwA5lDJHg",
    new MSendMsg(
      text,
      text,
      topic,
      "nick@routestory.net",
      "RouteStory",
      List(MTo("nick.stanch@gmail.com")),
      bcc_address = "",
      tracking_domain = "",
      signing_domain = "",
      return_path_domain = ""
    )
  )

  lazy val redis = RedisClient(
    "pub-redis-17158.eu-west-1-1.2.ec2.garantiadata.com",
    17158,
    password = Some("ElNpNkQqUUS6LLoV")
  )

  val siteRoutes =
    pathEndOrSingleSlash {
      getFromResource("index.html")
    } ~
    (path("signup") & post) {
      formFields('email) { email ⇒
        complete {
          MandrillAsyncClient.messagesSend(msg(email, "RouteStory invite")) map { _ ⇒
            getFromResource("thanks.html")
          }
        }
      }
    } ~
    (path("register") & get & parameter('id)) { id ⇒
      complete(async {
        val rand = Random.nextBoolean().toString
        if (await(redis.setnx(id, rand))) {
          MandrillAsyncClient.messagesSend(msg(s"$id: $rand", "RouteStory registration"))
          rand
        } else {
          await(redis.get[String](id)).get
        }
      })
    } ~
    pathPrefix("static") {
      getFromResourceDirectory("static")
    }
}
