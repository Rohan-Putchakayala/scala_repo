object CartesianProduct {
  def main(args: Array[String]): Unit = {
    val students = List("Asha", "Bala", "Chitra")
    val subjects = List("Math", "Physics")

    val result = for {
      student <- students
      subject <- subjects
      if student.length >= subject.length // use >= to include Asha-Math
    } yield (student, subject)

    println(result)
  }
}