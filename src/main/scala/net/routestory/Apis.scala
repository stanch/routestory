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

  lazy val hybridApi = net.routestory.couch.Api(
    couchDb,
    webApi
  )

  lazy val webApi = net.routestory.web.Api(
    AndroidClient(new AsyncHttpClient),
    endpointLogger,
    getExternalCacheDir
  )

  lazy val foursquareApi = resolvable.foursquare.Api(
    "0TORHPL0MPUG24YGBVNINGV2LREZJCD0XBCDCBMFC0JPDO05",
    "SIPSHLBOLADA2HW3RT44GE14OGBDNSM0VPBN4MSEWH2E4VLN",
    AndroidClient(new AsyncHttpClient),
    endpointLogger
  )

  lazy val flickrApi = resolvable.flickr.Api(
    "b8888816f155ed6f419b9a2348e16fee",
    AndroidClient(new AsyncHttpClient),
    endpointLogger
  )
}
