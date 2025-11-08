class Student(val name: String, val marks: Int)

object Student {
  def apply(name: String, marks: Int): Student = new Student(name, marks)
}

object RunStudent {
  def main(args: Array[String]): Unit = {
    val s1 = Student("Rohan", 85)
    println(s"Student: ${s1.name}, Marks: ${s1.marks}")
  }
}
