object PartialFunctions {
  val divide: PartialFunction[Int, Int] = {
    case x if x != 0 => 100 / x
  }

  def main(args: Array[String]): Unit = {
    println(divide.isDefinedAt(0)) // false
    println(divide.isDefinedAt(5)) // true
    println(divide(5))
  }
}
