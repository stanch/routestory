package net.routestory.api

import java.io.File

import net.routestory.json.JsonRules
import resolvable.EndpointLogger
import resolvable.http.HttpClient

trait Api {
  def httpClient: HttpClient
  def endpointLogger: EndpointLogger
  def mediaPath: File
}

object Api {
  def apply(client: HttpClient, logger: EndpointLogger, path: File) =
    new Api with Endpoints with Needs with JsonRules {
      val httpClient = client
      val endpointLogger = logger
      val mediaPath = path
    }
}