package com.alexitc.chromeapp

import play.api.libs.json.{Json, OFormat}

object Message {
  case class BgRequest(content: String) {
    def encode(): String = Json.toJson(this).toString()
  }

  object BgRequest {
    implicit val bgRequestFormat: OFormat[BgRequest] = Json.format[BgRequest]
    def decode(jsonString: String): BgRequest = Json.parse(jsonString).as[BgRequest]
  }

  case class BgResponse(oldPageId: String, newPageId: String) {
    def encode(): String = Json.toJson(this).toString()
  }
  object BgResponse {
    implicit val bgResponseFormat: OFormat[BgResponse] = Json.format[BgResponse]
    def decode(jsonString: String): BgResponse = Json.parse(jsonString).as[BgResponse]
  }
}
