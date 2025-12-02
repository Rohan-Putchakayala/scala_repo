package tables
import models.IdProof
import slick.jdbc.MySQLProfile.api._
class IdProofTable(tag: Tag) extends Table[IdProof](tag,"id_proofs") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def visitId = column[Long]("visit_id")
  def hashValue = column[String]("hash_value")

  def fk = foreignKey("fk_idproofs_visit", visitId, TableQuery[VisitTable])(_.id)
  def * =(id.?,visitId,hashValue) <> (IdProof.tupled,IdProof.unapply)

}
