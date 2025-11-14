package main

import scala.io.StdIn
import db.DatabaseConnection
import operations.TrafficDAO

object TrafficMain {
  def main(args: Array[String]): Unit = {

    DatabaseConnection.initializeTables() 

    var loop = true
    while (loop) {
      println(
        """
--- Smart Traffic System ---
1. Add Vehicle
2. Add Traffic Signal
3. Record Violation
4. Update Signal Status
5. View Vehicles
6. View Traffic Signals
7. View Violations
8. Exit
"""
      )

      val choice = StdIn.readLine("Enter choice: ")
      choice match {
        case "1" => TrafficDAO.addVehicle()
        case "2" => TrafficDAO.addTrafficSignal()
        case "3" => TrafficDAO.recordViolation()
        case "4" => TrafficDAO.updateSignalStatus()
        case "5" => TrafficDAO.viewVehicles()
        case "6" => TrafficDAO.viewSignals()
        case "7" => TrafficDAO.viewViolations()
        case "8" => loop = false
        case _   => println("Invalid choice!")
      }
    }

    println("Exiting...")
  }
}
