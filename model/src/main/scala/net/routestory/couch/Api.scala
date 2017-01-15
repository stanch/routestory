package net.routestory.couch

import com.couchbase.lite.Database
import net.routestory.json.JsonRules

case class Api(couch: Database, webApi: net.routestory.web.Api)
  extends Endpoints
  with Needs
  with JsonRules
  with Native
