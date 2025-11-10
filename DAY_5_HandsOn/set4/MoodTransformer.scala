object MoodTransformer {
  def moodChanger(prefix: String): String => String = {
    word => s"$prefix-$word-$prefix"
  }

  def main(args: Array[String]): Unit = {
    val happyMood = moodChanger("happy")
    val angryMood = moodChanger("angry")
    println(happyMood("day"))
    println(angryMood("crowd"))
  }
}