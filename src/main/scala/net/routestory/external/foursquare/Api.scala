package net.routestory.external.foursquare

case class Api(clientId: String, clientSecret: String, webApi: net.routestory.web.Api)
  extends Endpoints
  with Needs
  with JsonRules
