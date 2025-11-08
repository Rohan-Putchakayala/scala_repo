import scala.annotation.tailrec

object TailRecursion {
  @tailrec
  def factorial(n: Int, acc: Int = 1): Int =
    if (n <= 1) acc else factorial(n - 1, n * acc)

  def main(args: Array[String]): Unit = {
    println(factorial(5))
  }
}
