object ControlStructures {
  def main(args: Array[String]): Unit = {
    for (i <- 1 to 5) println(s"For loop: $i")

    var i = 0
    while (i < 3) {
      println(s"While loop: $i")
      i += 1
    }

    val day = "Mon"
    val result = day match {
      case "Mon" => "Start of week"
      case "Fri" => "Weekend soon"
      case _ => "Midweek"
    }
    println(result)
  }
}
