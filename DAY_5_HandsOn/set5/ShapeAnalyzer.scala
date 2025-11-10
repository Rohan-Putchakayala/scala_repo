case class Circle(r: Double)
case class Rectanglee(w: Double, h: Double)

object Shape_Analyzer {
  def area(shape: Any): Double = shape match {
    case Circle(r) => Math.PI * r * r
    case Rectanglee(w, h) => w * h
    case _ => -1.0
  }

  def main(args: Array[String]): Unit = {
    println(area(Circle(3)))
    println(area(Rectangle(4, 5)))
  }
}
