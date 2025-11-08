object CurryingExample {
  def add(a: Int)(b: Int): Int = a + b

  def main(args: Array[String]): Unit = {
    val add5 = add(5)
    println(add5(10))
  }
}
