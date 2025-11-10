object IntentionalCrasher {
  val safeDivide: PartialFunction[Int, String] = {
    case x if x != 0 => "Result: " + (100 / x)
  }

  def main(args: Array[String]): Unit = {
    val safe = safeDivide.lift
    println(safe(10))
    println(safe(0))
  }
}