object SentencePipeline {
  val trimSpaces: String => String = _.trim
  val toLower: String => String = _.toLowerCase
  val capitalizeFirst: String => String = s => s.head.toUpper + s.tail
  val processSentence: String => String = trimSpaces.andThen(toLower).andThen(capitalizeFirst)

  def main(args: Array[String]): Unit = {
    val messy = " HeLLo WOrld "
    println(processSentence(messy))
  }
}