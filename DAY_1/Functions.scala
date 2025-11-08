object Functions {
  def greet(): Unit = println("Hello Scala")

  def add(a: Int, b: Int): Int = a + b

  def multiply(a: Int, b: Int = 2): Int = a * b // default param

  def power(base: Int, exp: Int): Int = if (exp == 0) 1 else base * power(base, exp - 1)

  def main(args: Array[String]): Unit = {
    greet()
    println(add(3, 4))
    println(multiply(5))
    println(power(2, 3))
  }
}
