package net.routestory.data

import java.io.File

import net.routestory.api.Api
import net.routestory.zip.Save
import org.scalatest.FlatSpec
import resolvable.http.DispatchClient
import resolvable.{Resolvable, EndpointLogger}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Sandbox extends FlatSpec {
  val api = Api(new DispatchClient, EndpointLogger.println(success = true, failure = false), new File("."))

  it should "do something" in {
    val story = api.story("story-T9i5sZLKXzDDzaoGvu4osD").go

    story.flatMap { s ⇒
      Save(s, new File("story.zip"))
    } onComplete println
  }
}
