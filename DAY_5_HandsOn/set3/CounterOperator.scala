class Counter(val value: Int) {
  def +(other: Counter): Int = this.value + other.value
  def +(num: Int): Int = this.value + num
}

object CounterOperator {
  def main(args: Array[String]): Unit = {
    val a = new Counter(5)
    val b = new Counter(7)
    println(a + b)
    println(a + 10)
  }
}
