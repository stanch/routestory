package net.routestory.data

import java.io.File

import com.javadocmd.simplelatlng.LatLng
import net.routestory.web.Api
import net.routestory.zip.{Load, Save}
import org.scalatest.FlatSpec
import resolvable.http.DispatchClient
import resolvable.{Resolvable, EndpointLogger}

import scala.concurrent.ExecutionContext.Implicits.global

class Sandbox extends FlatSpec {
  val api = Api(new DispatchClient, EndpointLogger.println(success = true, failure = true), new File("."))
  val insta = net.routestory.external.instagram.Api("36601c1b02194ca59254e31cd3a1400d", api)

  it should "do something" in {
    val x = insta.nearbyPhotos(new LatLng(38.713811, -9.139386), 5000).go
    x onComplete println
  }
}
