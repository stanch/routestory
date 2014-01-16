package net.routestory.needs

import com.couchbase.lite.Database
import android.content.Context
import org.needs.http.HttpClient
import org.needs.EndpointLogger

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
