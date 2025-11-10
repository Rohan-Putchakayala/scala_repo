object LoginValidator {
  def validateLogin(username: String, password: String): Either[String, String] = {
    if (username.isEmpty) Left("Username missing")
    else if (password.isEmpty) Left("Password missing")
    else Right("Login successful")
  }

  def main(args: Array[String]): Unit = {
    println(validateLogin("", "123"))
    println(validateLogin("user", ""))
    println(validateLogin("user", "123"))
  }
}