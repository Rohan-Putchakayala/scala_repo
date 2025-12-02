package models

import play.api.libs.json._

object JsonFormats {

  implicit val visitorWrites = Json.writes[Visitor]
  implicit val visitorReads  = Json.reads[Visitor]

  implicit val visitWrites = Json.writes[Visit]
  implicit val visitReads  = Json.reads[Visit]

  implicit val idProofWrites = Json.writes[IdProof]
  implicit val idProofReads  = Json.reads[IdProof]


}
