package repos

import javax.inject._
import slick.jdbc.JdbcProfile
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import models.IdProof
import tables.IdProofTable
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for handling ID proof records.
 *
 * Provides a DBIO-based insert method so it can be composed inside a
 * larger transactional workflow (during visitor check-in). This ensures
 * ID proof insertion is atomic with visitor creation and visit creation.
 */
@Singleton
class IdProofRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
                           (implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  private val idProofs = TableQuery[IdProofTable]

  def insert(value: IdProof): DBIO[Long] =
    (idProofs returning idProofs.map(_.id)) += value
}

