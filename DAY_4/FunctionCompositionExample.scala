object FunctionCompositionExample {
  def main(args: Array[String]): Unit = {
    val add2 = (x: Int) => x + 2
    val multiply3 = (x: Int) => x * 3

    val composed1 = add2.andThen(multiply3)
    val composed2 = add2.compose(multiply3)

    println(s"andThen result: ${composed1(4)}")
    println(s"compose result: ${composed2(4)}")
  }
}
