object SafeDivider {
  def safeDivide(x: Int, y: Int): Option[Int] = if (y == 0) None else Some(x / y)

  def main(args: Array[String]): Unit = {
    val result = safeDivide(10, 0).getOrElse(-1)
    println(result)
  }
}