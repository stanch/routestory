package net.routestory.lounge

import scala.concurrent.future
import scala.concurrent.ExecutionContext.Implicits.global
import org.ektorp.android.http.AndroidHttpClient
import org.ektorp.impl.StdCouchDbInstance

trait RemoteCouch {
  object Remote {
    val couch = future {
      val client = (new AndroidHttpClient.Builder).connectionTimeout(10000).url("https://bag-routestory-net.herokuapp.com:443/").build()
      new StdCouchDbInstance(client).createConnector("story", false)
    }
  }
}
