package net.routestory.external.flickr

case class Api(apiKey: String, webApi: net.routestory.web.Api)
  extends Endpoints
  with Needs
  with JsonRules
