object Calculatorr {
  def add(a: Int, b: Int): Int = a + b
  def add(a: Double, b: Double): Double = a + b
}

object RunCalculator {
  def main(args: Array[String]): Unit = {
    println(Calculatorr.add(5, 10))
    println(Calculatorr.add(2.5, 3.5))
  }
}
