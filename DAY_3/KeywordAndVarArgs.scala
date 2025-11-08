object KeywordAndVarArgs {
  def greet(name: String = "User", age: Int = 18): Unit = println(s"Hello $name, age $age")

  def printAll(nums: Int*): Unit = nums.foreach(println)

  def main(args: Array[String]): Unit = {
    greet(age = 25, name = "Rohan")
    printAll(1, 2, 3, 4, 5)
  }
}
