object MergeLists {
  def main(args: Array[String]): Unit = {
    val a = List(1, 2)
    val b = List(3, 4)
    val c1 = a ++ b
    val c2 = a ::: b
    println(c1)
    println(c2)
    val v = Vector(5, 6)
    val combined = (a ++ b) ++ v
    println(combined)
  }
}