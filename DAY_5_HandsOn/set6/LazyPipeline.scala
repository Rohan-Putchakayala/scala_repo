object Pipeline {
  def apply[T](block: => T): LazyPipeline[T] = new LazyPipeline(block)
}

class LazyPipeline[T](block: => T) {
  lazy val result = block
  def map[R](f: T => R): LazyPipeline[R] = Pipeline(f(result))
}

object LazyPipelineApp {
  def main(args: Array[String]): Unit = {
    val p = Pipeline {
      println("Step 1: Preparing data")
      List(1, 2, 3)
    }.map { xs =>
      println("Step 2: Transforming data")
      xs.map(_ * 2)
    }
    println("Before accessing pipeline...")
    println("Result: " + p.result)
  }
}