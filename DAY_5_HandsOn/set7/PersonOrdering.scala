class Individual(val name: String, val age: Int)

object PersonOrdering {
  implicit class RichIndividual(p: Individual) {
    def >(other: Individual): Boolean = p.age > other.age
    def <(other: Individual): Boolean = p.age < other.age
    def >=(other: Individual): Boolean = p.age >= other.age
    def <=(other: Individual): Boolean = p.age <= other.age
  }

  def main(args: Array[String]): Unit = {
    val p1 = new Individual("Ravi", 25)
    val p2 = new Individual("Meena", 30)
    println(p1 < p2)
    println(p1 >= p2)
  }
}
