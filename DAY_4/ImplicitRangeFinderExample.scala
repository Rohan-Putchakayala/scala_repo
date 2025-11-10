object ImplicitRangeFinderExample {
  implicit class RangeFinder(start: Int) {
    def toRange(end: Int): List[Int] = (start to end).toList
  }

  def main(args: Array[String]): Unit = {
    val range = 1.toRange(5)
    println(range)
  }
}
