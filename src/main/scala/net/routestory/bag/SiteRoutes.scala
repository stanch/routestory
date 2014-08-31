package net.routestory.bag

import com.joypeg.scamandrill.client.MandrillAsyncClient
import com.joypeg.scamandrill.models.{MTo, MSendMsg, MSendMessage}

trait SiteRoutes { self: RouteStoryService ⇒
  def msg(email: String) = MSendMessage(
    "Saw-MObLFMXL4hwA5lDJHg",
    new MSendMsg(
      email,
      email,
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
    pathPrefix("static") {
      getFromResourceDirectory("static")
    }
}
