object Delayed_Greeter {
  def delayedMessage(delayMs: Int)(message: String): Unit = {
    Thread.sleep(delayMs)
    println(message)
  }

  def main(args: Array[String]): Unit = {
    val oneSecondSay = delayedMessage(1000)_
    oneSecondSay("Hello")
    oneSecondSay("Scala")
    oneSecondSay("Partial Application")
  }
}
