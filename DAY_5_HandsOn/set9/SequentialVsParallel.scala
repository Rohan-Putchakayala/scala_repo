import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util._

object SequentialVsParallel {
  def task(name: String, delay: Int): Future[String] = Future {
    Thread.sleep(delay)
    s"$name done"
  }

  def main(args: Array[String]): Unit = {
    val startSeq = System.currentTimeMillis()
    val seq = for {
      a <- task("A", 500)
      b <- task("B", 300)
      c <- task("C", 200)
    } yield List(a, b, c)
    seq.foreach(res => println("Sequential: " + res + ", Time: " + (System.currentTimeMillis() - startSeq)))

    val startPar = System.currentTimeMillis()
    val par = Future.sequence(List(task("A", 500), task("B", 300), task("C", 200)))
    par.foreach(res => println("Parallel: " + res + ", Time: " + (System.currentTimeMillis() - startPar)))
  }
}