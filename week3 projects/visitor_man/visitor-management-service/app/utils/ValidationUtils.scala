package utils

object ValidationUtils {

  def requireNonEmpty(name: String, value: String): Unit = {
    if (value.trim.isEmpty)
      throw new IllegalArgumentException(s"$name cannot be empty")
  }

  def requireAadhaar(aadhaar: String): Unit = {
    if (!aadhaar.matches("^[0-9]{12}$"))
      throw new IllegalArgumentException("Aadhaar must be exactly 12 digits")
  }
}
