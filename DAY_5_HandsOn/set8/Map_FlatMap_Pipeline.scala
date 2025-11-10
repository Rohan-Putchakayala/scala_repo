import scala.util.Try

object Map_FlatMap_Pipeline {
  def main(args: Array[String]): Unit = {
    val data = List("10", "20", "x", "30")

    val result = data.flatMap(s => Try(s.toInt).toOption).map(x => x * x)

    println(result)
  }
}
