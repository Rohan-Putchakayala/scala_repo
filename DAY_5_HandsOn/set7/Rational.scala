case class Rational(n: Int, d: Int) {
  def /(that: Rational): Rational = Rational(this.n * that.d, this.d * that.n)
}

object RationalApp {
  implicit def intToRational(n: Int): Rational = Rational(n, 1)

  def main(args: Array[String]): Unit = {
    val r = 1 / Rational(2, 3)
    println(r)
  }
}