package net.routestory.web

import java.io.File

import net.routestory.json.JsonRules
import resolvable.EndpointLogger
import resolvable.http.HttpClient

case class Api(httpClient: HttpClient, endpointLogger: EndpointLogger, mediaPath: File)
  extends Endpoints
  with Needs
  with JsonRules
