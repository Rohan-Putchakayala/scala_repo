object ArrowMap {
  def main(args: Array[String]): Unit = {
    val animals = Map(
      "dog" -> "bark",
      "cat" -> "meow",
      "cow" -> "moo"
    )
    val updated = animals + ("lion" -> "roar")
    println(updated("cow"))
    println(updated.getOrElse("tiger", "unknown"))
  }
}