import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._

object SafeComposition {
  def riskyOperation(): Future[Int] = Future {
    val n = scala.util.Random.nextInt(3)
    if (n == 0) throw new RuntimeException("Failed!")
    n
  }

  def main(args: Array[String]): Unit = {
    riskyOperation().recover { case _ => -1 }.foreach(res => println("Recover: " + res))
    riskyOperation().recoverWith { case _ => riskyOperation() }.foreach(res => println("RecoverWith retry: " + res))
    Thread.sleep(2000)
  }
}