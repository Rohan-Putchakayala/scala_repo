case class Employee(name: String, id: Int)

object RunEmployee {
  def main(args: Array[String]): Unit = {
    val e1 = Employee("Alice", 101)
    val e2 = Employee("Alice", 101)
    println(e1 == e2) // true because case classes compare by value
    println(e1.copy(name = "Bob"))
  }
}
