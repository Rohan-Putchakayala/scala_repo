class Rectangle(var width: Double, var height: Double) {
  def this(size: Double) = this(size, size) // auxiliary constructor
  def area: Double = width * height
}

object RunRectangle {
  def main(args: Array[String]): Unit = {
    val rect1 = new Rectangle(5, 10)
    val rect2 = new Rectangle(7)
    println(s"Rect1 area: ${rect1.area}, Rect2 area: ${rect2.area}")
  }
}
