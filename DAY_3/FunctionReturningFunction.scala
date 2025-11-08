object FunctionReturningFunction {
  def multiplier(factor: Int): Int => Int = (x: Int) => x * factor

  def main(args: Array[String]): Unit = {
    val double = multiplier(2)
    println(double(10))
  }
}
