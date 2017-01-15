package net.routestory

import resolvable.http.AndroidClient
import resolvable.{ Endpoint, EndpointLogger }
import android.util.Log
import com.loopj.android.http.AsyncHttpClient

trait Apis { self: RouteStoryApp ⇒
  object endpointLogger extends EndpointLogger {
    def logStart(pt: Endpoint) = Log.d("Needs", s"--> Downloading $pt")
    def logFailure(pt: Endpoint)(error: Throwable) = {
      Log.d("Needs", s"--x Failed $pt")
    }
    def logSuccess(pt: Endpoint)(data: pt.Data) = ()
  }

  lazy val webApi = net.routestory.web.Api(
    AndroidClient(new AsyncHttpClient),
    endpointLogger,
    getExternalCacheDir
  )

  lazy val hybridApi = net.routestory.couch.Api(
    couchDb,
    webApi
  )

  lazy val foursquareApi = net.routestory.external.foursquare.Api(
    "0TORHPL0MPUG24YGBVNINGV2LREZJCD0XBCDCBMFC0JPDO05",
    "SIPSHLBOLADA2HW3RT44GE14OGBDNSM0VPBN4MSEWH2E4VLN",
    webApi
  )

  lazy val flickrApi = net.routestory.external.flickr.Api(
    "b8888816f155ed6f419b9a2348e16fee",
    webApi
  )

  lazy val instagramApi = net.routestory.external.instagram.Api(
    "36601c1b02194ca59254e31cd3a1400d",
    webApi
  )
}
