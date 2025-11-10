object ImplicitFunctionExample {
  implicit def intToString(x: Int): String = x.toString

  def printString(str: String): Unit = println(s"String is: $str")

  def main(args: Array[String]): Unit = {
    printString(100)
  }
}
