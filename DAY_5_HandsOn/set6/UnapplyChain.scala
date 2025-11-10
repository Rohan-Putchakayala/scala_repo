case class Address(city: String, pincode: Int)
case class Citizen(name: String, address: Address)

object UnapplyChain {
  def main(args: Array[String]): Unit = {
    val p = Citizen("Ravi", Address("Chennai", 600001))
    p match {
      case Citizen(_, Address(city, pin)) if city.startsWith("C") =>
        println(s"$city - $pin")
      case _ => println("No match")
    }
  }
}
