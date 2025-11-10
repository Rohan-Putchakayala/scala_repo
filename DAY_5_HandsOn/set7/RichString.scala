object RichStringApp {
  implicit class RichString(val s: String) extends AnyVal {
    def *(times: Int): String = s.repeat(times)
    def ~(other: String): String = s + " " + other
  }

  def main(args: Array[String]): Unit = {
    println("Hi" * 3)
    println("Hello" ~ "World")
  }
}