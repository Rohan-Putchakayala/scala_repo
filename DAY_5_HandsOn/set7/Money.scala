case class Money(amount: Double)(implicit precision: Double) {
  def +(that: Money): Money = Money(Math.round((this.amount + that.amount)/precision)*precision)
  def -(that: Money): Money = Money(Math.round((this.amount - that.amount)/precision)*precision)
}

object MoneyApp {
  implicit val roundingPrecision: Double = 0.05
  def main(args: Array[String]): Unit = {
    val m1 = Money(10.23)
    val m2 = Money(5.19)
    println(m1 + m2)
  }
}