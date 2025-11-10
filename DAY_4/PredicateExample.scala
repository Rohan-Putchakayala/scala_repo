object PredicateExample {
  def main(args: Array[String]): Unit = {
    val isEven: Int => Boolean = _ % 2 == 0
    val numbers = List(1, 2, 3, 4, 5)
    val evens = numbers.filter(isEven)
    println(evens)
  }
}
