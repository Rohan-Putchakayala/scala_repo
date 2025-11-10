object FunctionPipelines {
  val trim: String => String = _.trim
  val toInt: String => Int = _.toInt
  val doubleIt: Int => Int = _ * 2

  def main(args: Array[String]): Unit = {
    val composed = doubleIt compose toInt compose trim
    val andThened = trim andThen toInt andThen doubleIt
    println(composed(" 21 "))   // 42
    println(andThened(" 21 "))  // 42
  }
}
