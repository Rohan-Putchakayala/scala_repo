object Retry_Logic {
  def fetchData(): Int = {
    val n = scala.util.Random.nextInt(3)
    if (n == 0) throw new RuntimeException("Network fail")
    n
  }

  def retry[T](times: Int)(op: => T): Option[T] = {
    try Some(op)
    catch {
      case _: Throwable if times > 1 => retry(times - 1)(op)
      case _: Throwable => None
    }
  }

  def main(args: Array[String]): Unit = {
    val data = retry(3)(fetchData())
    println(data)
  }
}
