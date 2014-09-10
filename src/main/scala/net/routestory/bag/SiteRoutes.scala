package net.routestory.bag

import com.joypeg.scamandrill.client.MandrillAsyncClient
import com.joypeg.scamandrill.models.{MTo, MSendMsg, MSendMessage}
import redis.RedisClient
import scala.async.Async._
import scala.util.Random

trait SiteRoutes { self: RouteStoryService ⇒
  def msg(text: String) = MSendMessage(
    "Saw-MObLFMXL4hwA5lDJHg",
    new MSendMsg(
      text,
      text,
      "RouteStory registration",
      "nick@routestory.net",
      "RouteStory",
      List(MTo("nick.stanch@gmail.com")),
      bcc_address = "",
      tracking_domain = "",
      signing_domain = "",
      return_path_domain = ""
    )
  )

  val redis = RedisClient(
    "redis://rediscloud@pub-redis-17158.eu-west-1-1.2.ec2.garantiadata.com:17158", 17158,
    Some("ElNpNkQqUUS6LLoV")
  )

  val siteRoutes =
    pathEndOrSingleSlash {
      getFromResource("index.html")
    } ~
    (path("signup") & post) {
      formFields('email) { email ⇒
        complete {
          MandrillAsyncClient.messagesSend(msg(email)) map { _ ⇒
            "Thanks for your interest in RouteStory! You’ll hear from us shortly."
          }
        }
      }
    } ~
    (path("register") & get & parameter('id)) { id ⇒
      complete(async {
        val registered = await(redis.setnx(id, Random.nextBoolean().toString))
        val value = await(redis.get[String](id))
        if (registered) msg(s"$id: $value")
        value
      })
    } ~
    pathPrefix("static") {
      getFromResourceDirectory("static")
    }
}
