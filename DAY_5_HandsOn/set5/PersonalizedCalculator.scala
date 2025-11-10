object PersonalizedCalculator {
  def calculate(op: String)(x: Int, y: Int): Int = op match {
    case "add" => x + y
    case "sub" => x - y
    case "mul" => x * y
    case "div" => x / y
  }

  def main(args: Array[String]): Unit = {
    val add = calculate("add")_
    val multiply = calculate("mul")_
    println(add(10, 5))
    println(multiply(3, 4))
  }
}