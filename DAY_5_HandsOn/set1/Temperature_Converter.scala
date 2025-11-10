object Temperature_Converter {

  def convertTemp(value: Double, scale: String): Double = {
    if (scale == "C") value * 9 / 5 + 32
    else if (scale == "F") (value - 32) * 5 / 9
    else value
  }

  def main(args: Array[String]): Unit = {
    println(convertTemp(0, "C"))
    println(convertTemp(212, "F"))
    println(convertTemp(50, "X"))
  }
}
