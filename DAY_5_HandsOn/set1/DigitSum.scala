object DigitSum {
  def digitSum(n: Int): Int = {
    if (n == 0) 0
    else (n % 10) + digitSum(n / 10)
  }

  def main(args: Array[String]): Unit = {
    val num = 1345
    println(digitSum(num))
  }
}
