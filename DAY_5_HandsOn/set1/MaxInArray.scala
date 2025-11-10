object MaxInArray {
  def maxInArray(arr: Array[Int]): Int = {
    @annotation.tailrec
    def loop(i: Int, currentMax: Int): Int = {
      if (i == arr.length) currentMax
      else {
        val newMax = if (arr(i) > currentMax) arr(i) else currentMax
        loop(i + 1, newMax)
      }
    }
    if (arr.isEmpty) throw new NoSuchElementException("Array is empty")
    else loop(1, arr(0))
  }

  def main(args: Array[String]): Unit = {
    val nums = Array(5, 9, 3, 7, 2)
    println(maxInArray(nums)) // Output: 9
  }
}
