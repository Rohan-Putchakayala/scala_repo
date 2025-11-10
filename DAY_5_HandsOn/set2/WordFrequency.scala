import scala.collection.immutable.ListMap

object WordFrequency {
  def main(args: Array[String]): Unit = {
    val lines = List(
      "Scala is powerful",
      "Scala is concise",
      "Functional programming is powerful"
    )

    val words = for {
      line <- lines
      word <- line.split(" ")
    } yield word

    val freqMap = words.groupBy(identity).view.mapValues(_.size).toMap

    // Sort by keys alphabetically
    val sortedFreqMap = ListMap(freqMap.toSeq.sortBy(_._1): _*)

    println(sortedFreqMap)
  }
}
