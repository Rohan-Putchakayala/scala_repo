case class Meter(value: Double) {
  def +(other: Meter): Meter = Meter(this.value + other.value)
}

object ImplicitOperatorOverloadExample {
  implicit def doubleToMeter(d: Double): Meter = Meter(d)

  def main(args: Array[String]): Unit = {
    val total: Meter = 5.0 + 10.0 
    println(s"Total distance: ${total.value} meters")
  }
}
