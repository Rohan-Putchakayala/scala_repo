import scala.concurrent._
import ExecutionContext.Implicits.global

object FutureCreationExample {
  def main(args: Array[String]): Unit = {
    val f = Future {
      println("Inside Future")
      10 + 20
    }
  }
}
