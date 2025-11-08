object Discount {
  def apply(price: Double, rate: Double = 0.1): Double = price - (price * rate)
}

object RunDiscount {
  def main(args: Array[String]): Unit = {
    println(Discount(100))      // uses default 10%
    println(Discount(100, 0.2)) // 20%
  }
}
