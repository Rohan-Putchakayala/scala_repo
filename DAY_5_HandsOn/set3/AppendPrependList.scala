object AppendPrependList {
  def main(args: Array[String]): Unit = {
    val nums = List(2, 4, 6)
    val addEnd = nums :+ 8
    val addStart = 0 +: addEnd
    println(addEnd)
    println(addStart)
  }
}
