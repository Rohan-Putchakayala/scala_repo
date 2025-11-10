object ExceptionHandlingExample {
  def divide(a: Int, b: Int): Int = {
    try {
      a / b
    } catch {
      case e: ArithmeticException =>
        println("Cannot divide by zero")
        0
    } finally {
      println("Division attempted")
    }
  }

  def main(args: Array[String]): Unit = {
    println(divide(10, 2))
    println(divide(10, 0))
  }
}
