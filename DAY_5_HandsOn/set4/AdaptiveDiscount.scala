object AdaptiveDiscount {
  def discountStrategy(memberType: String): Double => Double = memberType match {
    case "gold" => amount => amount * 0.8
    case "silver" => amount => amount * 0.9
    case _ => amount => amount
  }

  def main(args: Array[String]): Unit = {
    val goldDiscount = discountStrategy("gold")
    val silverDiscount = discountStrategy("silver")
    val regularDiscount = discountStrategy("none")
    println(goldDiscount(1000))
    println(silverDiscount(1000))
    println(regularDiscount(1000))
  }
}