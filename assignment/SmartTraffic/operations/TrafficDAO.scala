package operations

import db.DatabaseConnection
import scala.io.StdIn
import java.sql.Connection

object TrafficDAO {

  def addVehicle(): Unit = {
    val plate = StdIn.readLine("License Plate: ")
    val vtype = StdIn.readLine("Vehicle Type: ")
    val owner = StdIn.readLine("Owner Name: ")

    val sql = "INSERT INTO Vehicles (license_plate, vehicle_type, owner_name) VALUES (?, ?, ?)"

    val conn = DatabaseConnection.getConnection()
    val ps = conn.prepareStatement(sql)
    ps.setString(1, plate)
    ps.setString(2, vtype)
    ps.setString(3, owner)
    ps.executeUpdate()
    println("✔ Vehicle added!")

    ps.close()
    conn.close()
  }

  def addTrafficSignal(): Unit = {
    val loc = StdIn.readLine("Signal Location: ")
    val status = StdIn.readLine("Status (green/yellow/red): ")

    val sql = "INSERT INTO TrafficSignals (location, status) VALUES (?, ?)"

    val conn = DatabaseConnection.getConnection()
    val ps = conn.prepareStatement(sql)
    ps.setString(1, loc)
    ps.setString(2, status)
    ps.executeUpdate()
    println("✔ Traffic signal added!")

    ps.close()
    conn.close()
  }

  def recordViolation(): Unit = {
    val vidStr = StdIn.readLine("Vehicle ID (number): ")
    val sidStr = StdIn.readLine("Signal ID (number): ")
    val vtype  = StdIn.readLine("Violation Type: ")

    if (!vidStr.matches("\\d+") || !sidStr.matches("\\d+")) {
      println("❌ Vehicle ID and Signal ID must be numbers!")
      return
    }

    val vid = vidStr.toInt
    val sid = sidStr.toInt

    val conn = DatabaseConnection.getConnection()
    val check = conn.prepareStatement("SELECT vehicle_id FROM Vehicles WHERE vehicle_id = ?")
    check.setInt(1, vid)
    val rs = check.executeQuery()

    if (!rs.next()) {
      println(s"❌ Vehicle ID $vid does NOT exist. Add the vehicle first.")
      rs.close()
      check.close()
      conn.close()
      return
    }

    val sql = "INSERT INTO Violations (vehicle_id, signal_id, violation_type, timestamp) VALUES (?, ?, ?, NOW())"
    val ps = conn.prepareStatement(sql)
    ps.setInt(1, vid)
    ps.setInt(2, sid)
    ps.setString(3, vtype)
    ps.executeUpdate()
    println("✔ Violation recorded!")

    ps.close()
    check.close()
    conn.close()
  }

  def updateSignalStatus(): Unit = {
    val sid = StdIn.readLine("Signal ID: ").toInt
    val status = StdIn.readLine("New Status (green/yellow/red): ")

    val sql = "UPDATE TrafficSignals SET status = ? WHERE signal_id = ?"
    val conn = DatabaseConnection.getConnection()
    val ps = conn.prepareStatement(sql)
    ps.setString(1, status)
    ps.setInt(2, sid)
    ps.executeUpdate()
    println("✔ Signal status updated!")

    ps.close()
    conn.close()
  }

  def viewVehicles(): Unit = {
    val sql = "SELECT * FROM Vehicles"
    val conn = DatabaseConnection.getConnection()
    val rs = conn.prepareStatement(sql).executeQuery()

    println("\n--- Vehicles ---")
    while rs.next() do println(
      s"${rs.getInt("vehicle_id")} | ${rs.getString("license_plate")} | ${rs.getString("vehicle_type")} | ${rs.getString("owner_name")}"
    )

    rs.close()
    conn.close()
  }

  def viewSignals(): Unit = {
    val sql = "SELECT * FROM TrafficSignals"
    val conn = DatabaseConnection.getConnection()
    val rs = conn.prepareStatement(sql).executeQuery()

    println("\n--- Traffic Signals ---")
    while rs.next() do println(
      s"${rs.getInt("signal_id")} | ${rs.getString("location")} | ${rs.getString("status")}"
    )

    rs.close()
    conn.close()
  }

  def viewViolations(): Unit = {
    val sql = "SELECT * FROM Violations"
    val conn = DatabaseConnection.getConnection()
    val rs = conn.prepareStatement(sql).executeQuery()

    println("\n--- Violations ---")
    while rs.next() do println(
      s"${rs.getInt("violation_id")} | Vehicle:${rs.getInt("vehicle_id")} | Signal:${rs.getInt("signal_id")} | ${rs.getString("violation_type")} | ${rs.getTimestamp("timestamp")}"
    )

    rs.close()
    conn.close()
  }
}
