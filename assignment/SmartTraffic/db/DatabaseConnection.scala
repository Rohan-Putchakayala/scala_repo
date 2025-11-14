//> using dep "mysql:mysql-connector-java:8.0.33"

package db

import java.sql.{Connection, DriverManager}
import scala.util.Using

object DatabaseConnection {

  private val url =
    "jdbc:mysql://azuremysql8823.mysql.database.azure.com:3306/XXXXXXXXX"
  private val user = "XXXXXXXXX"
  private val password = "XXXXXXXXX"
  try Class.forName("com.mysql.cj.jdbc.Driver")
  catch {
    case e: Exception => println(s"MySQL Driver load error: ${e.getMessage}")
  }
  def getConnection(): Connection =
    DriverManager.getConnection(url, user, password)

  def initializeTables(): Unit = {
    Using.resource(getConnection()) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS Vehicles (
            vehicle_id INT AUTO_INCREMENT PRIMARY KEY,
            license_plate VARCHAR(20),
            vehicle_type VARCHAR(20),
            owner_name VARCHAR(100)
          )
          """
        )

        stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS TrafficSignals (
            signal_id INT AUTO_INCREMENT PRIMARY KEY,
            location VARCHAR(100),
            status VARCHAR(10)
          )
          """
        )

        stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS Violations (
            violation_id INT AUTO_INCREMENT PRIMARY KEY,
            vehicle_id INT,
            signal_id INT,
            violation_type VARCHAR(50),
            timestamp DATETIME,
            FOREIGN KEY (vehicle_id) REFERENCES Vehicles(vehicle_id),
            FOREIGN KEY (signal_id) REFERENCES TrafficSignals(signal_id)
          )
          """
        )

        println("✔ Tables verified/created.")
      }
    }
  }
}
