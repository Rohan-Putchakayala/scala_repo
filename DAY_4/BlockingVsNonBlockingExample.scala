import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._

object BlockingVsNonBlockingExample {
  def main(args: Array[String]): Unit = {
    println("Non-blocking example starts...")

    val future = Future {
      Thread.sleep(2000)
      println("Future task completed")
      42
    }

    println("Main thread continues (non-blocking)")

    val result = Await.result(future, 3.seconds)
    println(s"Blocking result: $result")
  }
}
