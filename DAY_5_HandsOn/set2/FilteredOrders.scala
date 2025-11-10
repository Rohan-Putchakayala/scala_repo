case class Order(id: Int, amount: Double, status: String)

object FilteredOrders {
  def main(args: Array[String]): Unit = {
    val orders = List(
      Order(1, 1200.0, "Delivered"),
      Order(2, 250.5, "Pending"),
      Order(3, 980.0, "Delivered"),
      Order(4, 75.0, "Cancelled")
    )

    val result = for {
      o <- orders
      if o.status == "Delivered" && o.amount > 500
    } yield s"Order #${o.id} -> ₹${o.amount}"

    result.foreach(println)
  }
}
