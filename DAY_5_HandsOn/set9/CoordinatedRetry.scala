import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._

object CoordinatedRetry {
  def fetchDataFromServer(server: String): Future[String] = Future {
    if (scala.util.Random.nextBoolean()) throw new RuntimeException("Failed")
    s"$server success"
  }

  def fetchWithRetry(server: String, maxRetries: Int): Future[String] = {
    fetchDataFromServer(server).recoverWith {
      case _ if maxRetries > 1 => fetchWithRetry(server, maxRetries - 1)
    }
  }

  def main(args: Array[String]): Unit = {
    fetchWithRetry("Server-1", 3).onComplete(res => println(res))
    Thread.sleep(2000)
  }
}