object MultiplicationTable {
  def multiplicationTable(n: Int): List[String] = {
    for {
      i <- 1 to n
      j <- 1 to n
    } yield s"$i x $j = ${i * j}"
  }.toList

  def main(args: Array[String]): Unit = {
    val table = multiplicationTable(3)
    table.foreach(println)
  }
}
