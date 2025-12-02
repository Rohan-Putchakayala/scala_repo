package models

case class IdProof(
                    id: Option[Long],
                    visitId: Long,
                    hashValue: String
                  )
