import scala.annotation.tailrec

object TailFactorial {
  def factorial(n: Int): Int = {
    @tailrec
    def helper(acc: Int, n: Int): Int = {
      if (n == 0) acc
      else {
        println(s"[acc=$acc, n=$n]")
        helper(acc * n, n - 1)
      }
    }
    helper(1, n)
  }

  def main(args: Array[String]): Unit = {
    factorial(5)
  }
}
