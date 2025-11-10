import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._

object ChainingDependentFutures {
  def getUser(id: Int): Future[String] = Future { s"User$id" }
  def getOrders(user: String): Future[List[String]] = Future { List(s"$user-order1", s"$user-order2") }
  def getOrderTotal(order: String): Future[Double] = Future { scala.util.Random.between(10.0, 100.0) }

  def main(args: Array[String]): Unit = {
    val total = for {
      user <- getUser(42)
      orders <- getOrders(user)
      totals <- Future.sequence(orders.map(getOrderTotal))
    } yield totals.sum
    total.foreach(sum => println("Total amount: " + sum))
    Thread.sleep(2000)
  }
}