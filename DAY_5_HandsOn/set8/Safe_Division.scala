object Safe_Division {
  def safeDivide(a: Int, b: Int): Either[String, Double] =
    if (b == 0) Left("Division by zero") else Right(a.toDouble / b)

  def main(args: Array[String]): Unit = {
    val pairs = List((10, 2), (5, 0), (8, 4))
    val results = pairs.map { case (a, b) => safeDivide(a, b) }
    val valids = results.collect { case Right(v) => v }
    val errors = results.collect { case Left(e) => e }
    println(s"Valid results: $valids")
    println(s"Errors: $errors")
  }
}
