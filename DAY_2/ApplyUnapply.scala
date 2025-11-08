class BankAccount(val balance: Double)

object BankAccount {
  def apply(balance: Double): BankAccount = new BankAccount(balance)
  def unapply(acc: BankAccount): Option[Double] = Some(acc.balance)
}

object RunBank {
  def main(args: Array[String]): Unit = {
    val acc = BankAccount(5000)
    acc match {
      case BankAccount(b) => println(s"Account balance is $$b")
    }
  }
}
