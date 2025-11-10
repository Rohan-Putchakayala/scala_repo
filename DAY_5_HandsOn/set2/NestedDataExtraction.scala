object NestedDataExtraction {
  def main(args: Array[String]): Unit = {
    val departments = List(
      ("IT", List("Ravi", "Meena")),
      ("HR", List("Anita")),
      ("Finance", List("Vijay", "Kiran"))
    )

    val result = for {
      (dept, employees) <- departments
      emp <- employees
    } yield s"$dept: $emp"

    result.foreach(println)
  }
}
