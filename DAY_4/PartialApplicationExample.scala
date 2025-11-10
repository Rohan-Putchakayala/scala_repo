object PartialApplicationExample {
  def multiply(a: Int, b: Int): Int = a * b

  def main(args: Array[String]): Unit = {
    val double = multiply(2, _: Int)
    println(double(5))
  }
}
