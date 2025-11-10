class Calculator {
  private def square(x: Int): Int = x * x

  def areaOfSquare(side: Int): Int = square(side)
}

object PrivateMethodExample {
  def main(args: Array[String]): Unit = {
    val calc = new Calculator()
    println(calc.areaOfSquare(5))
  }
}
