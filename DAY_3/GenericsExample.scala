// Define a generic class Box that stores a value of any type
class Box[T](value: T) {
  def getValue: T = value
}

// Create a companion object with apply() for easier instantiation
object Box {
  def apply[T](value: T): Box[T] = new Box[T](value)
}

// Create instances of Box inside an object with main method
object GenericsExampleApp {
  def main(args: Array[String]): Unit = {
    val intBox = Box[Int](42)   
    val strBox = Box("Scala Rocks!")     // using companion object apply()

    println(intBox.getValue)
    println(strBox.getValue)
  }
}
