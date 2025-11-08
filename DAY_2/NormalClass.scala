class Person(val name: String, val age: Int) {
  def greet(): Unit = println(s"Hello, my name is $name and I'm $age years old.")
}

object RunPerson {
  def main(args: Array[String]): Unit = {
    val p = new Person("Rohan", 24)
    p.greet()
  }
}
