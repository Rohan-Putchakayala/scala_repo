object HigherOrderFunctions {
  def calculate(x: Int, y: Int, f: (Int, Int) => Int): Int = f(x, y)

  def main(args: Array[String]): Unit = {
    println(calculate(3, 4, _ + _))
    println(calculate(10, 5, _ - _))
  }
}
