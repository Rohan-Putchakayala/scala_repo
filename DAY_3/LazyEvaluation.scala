object LazyEvaluation {
  lazy val heavyComputation = {
    println("Computing...")
    50 * 10
  }

  def main(args: Array[String]): Unit = {
    println("Before accessing lazy val")
    println(heavyComputation)
    println("After accessing lazy val")
  }
}
