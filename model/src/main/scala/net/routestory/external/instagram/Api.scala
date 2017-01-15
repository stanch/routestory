package net.routestory.external.instagram

case class Api(clientId: String, webApi: net.routestory.web.Api)
  extends Endpoints
  with Needs
  with JsonRules
