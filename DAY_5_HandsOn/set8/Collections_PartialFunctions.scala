object Collections_PartialFunctions {
  def main(args: Array[String]): Unit = {
    val items = List(1, "apple", 3.5, "banana", 42)
    val result = items.collect { case i: Int => i * 2 }
    println(result)
  }
}
