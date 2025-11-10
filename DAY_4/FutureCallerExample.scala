import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

object FutureCallerExample {
  def main(args: Array[String]): Unit = {
    val f = Future {
      Thread.sleep(1000)
      10 / 2
    }

    f.onComplete {
      case Success(value) => println(s"Result: $value")
      case Failure(ex) => println(s"Error: ${ex.getMessage}")
    }

    Thread.sleep(2000)
  }
}
