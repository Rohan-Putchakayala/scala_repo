case class Vector2D(x: Double, y: Double) {
  def +(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)
}

object RunVector {
  def main(args: Array[String]): Unit = {
    val v1 = Vector2D(2, 3)
    val v2 = Vector2D(4, 1)
    println(v1 + v2)
  }
}
