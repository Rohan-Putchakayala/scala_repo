object Array_Puzzle {
  def mirrorArray(arr: Array[Int]): Array[Int] = {
    val n = arr.length
    val mirrored = for (i <- 0 until (2 * n)) yield {
      if (i < n) arr(i)
      else arr(2 * n - i - 1)
    }
    mirrored.toArray
  }

  def main(args: Array[String]): Unit = {
    val input = Array(1, 2, 3)
    val result = mirrorArray(input)
    println(input.mkString("Array(", ", ", ")"))
    println(result.mkString("Array(", ", ", ")"))
  }
}
