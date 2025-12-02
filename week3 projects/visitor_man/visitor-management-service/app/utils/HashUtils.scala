package utils

import java.security.MessageDigest

object HashUtils {
  def sha256(value: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
