object MapReduceFilter {
  def main(args: Array[String]): Unit = {
    val nums = List(1, 2, 3, 4, 5)
    val squares = nums.map(x => x * x)
    val evens = nums.filter(_ % 2 == 0)
    val sum = nums.reduce(_ + _)
    println(s"Squares: $squares")
    println(s"Evens: $evens")
    println(s"Sum: $sum")
  }
}
