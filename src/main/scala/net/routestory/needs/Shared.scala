package net.routestory.needs

import com.couchbase.lite.Database
import android.content.Context
import resolvable.http.HttpClient
import resolvable.EndpointLogger

trait Shared {
  val couchDb: Database
  val appCtx: Context
  val httpClient: HttpClient
  val endpointLogger: EndpointLogger
}

trait Api
  extends Shared
  with Endpoints
  with LoadingFormats
  with Needs
