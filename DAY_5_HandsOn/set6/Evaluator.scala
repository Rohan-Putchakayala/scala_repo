object Evaluator {
  def apply(block: => Any): Unit = {
    println("Evaluating block...")
    val result = block
    println(s"Result = $result")
  }
}

object EvaluatorApp {
  def main(args: Array[String]): Unit = {
    Evaluator {
      val x = 5
      val y = 3
      x * y + 2
    }
  }
}