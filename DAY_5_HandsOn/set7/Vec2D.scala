case class Vec2D(x: Int, y: Int) {
  def +(v: Vec2D): Vec2D = Vec2D(x + v.x, y + v.y)
  def -(v: Vec2D): Vec2D = Vec2D(x - v.x, y - v.y)
  def *(s: Int): Vec2D = Vec2D(x * s, y * s)
}

object Vec2DApp {
  implicit class IntOps(val s: Int) extends AnyVal {
    def *(v: Vec2D): Vec2D = v * s
  }

  def main(args: Array[String]): Unit = {
    val v1 = Vec2D(2, 3)
    val v2 = Vec2D(4, 1)
    println(v1 + v2)
    println(v1 - v2)
    println(v1 * 3)
    println(3 * v1)
  }
}
