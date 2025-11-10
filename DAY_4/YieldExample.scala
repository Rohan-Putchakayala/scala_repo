object YieldExample {
  def main(args: Array[String]): Unit = {
    val numbers = List(1, 2, 3, 4, 5)
    val doubled = for (n <- numbers) yield n * 2
    println(doubled)
  }
}
