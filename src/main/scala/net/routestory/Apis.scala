package net.routestory

import org.needs.http.AndroidClient
import org.needs.{ Endpoint, EndpointLogger }
import android.util.Log
import com.loopj.android.http.AsyncHttpClient
import org.needs.foursquare.FoursquareApi
import org.needs.flickr.FlickrApi

trait Apis { self: RouteStoryApp ⇒
  object endpointLogger extends EndpointLogger {
    def logFetching(pt: Endpoint) = Log.d("Needs", s"--> Downloading $pt")
  }

  object api extends needs.Api {
    val endpointLogger = self.endpointLogger
    val httpClient = AndroidClient(new AsyncHttpClient())
    val appCtx = self
    val couchDb = self.couchDb
  }

  lazy val foursquareApi = new FoursquareApi(
    "0TORHPL0MPUG24YGBVNINGV2LREZJCD0XBCDCBMFC0JPDO05",
    "SIPSHLBOLADA2HW3RT44GE14OGBDNSM0VPBN4MSEWH2E4VLN",
    AndroidClient(new AsyncHttpClient),
    endpointLogger
  )

  lazy val flickrApi = new FlickrApi(
    "b8888816f155ed6f419b9a2348e16fee",
    AndroidClient(new AsyncHttpClient),
    endpointLogger
  )
}
