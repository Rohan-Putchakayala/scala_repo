package com.smartfleet.common.database

import com.smartfleet.common.models._
import com.typesafe.config.Config
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, Timestamp}
import scala.collection.mutable.ListBuffer
import scala.util.{Try, Success, Failure}

/**
 * MySQL database access layer for fleet operational data
 */
class MySQLDatabase(config: Config) {

  private val url = config.getString("mysql.url")
  private val username = config.getString("mysql.username")
  private val password = config.getString("mysql.password")
  private val driver = config.getString("mysql.driver")

  // Load MySQL driver
  Class.forName(driver)

  private def getConnection: Connection = {
    DriverManager.getConnection(url, username, password)
  }

  /**
   * Initialize all MySQL tables
   */
  def initializeTables(): Try[Unit] = Try {
    val connection = getConnection
    try {
      // Create fleet table
      val createFleetTable = """
        CREATE TABLE IF NOT EXISTS fleet (
          fleet_id INT PRIMARY KEY,
          fleet_name VARCHAR(255) NOT NULL,
          city VARCHAR(255) NOT NULL,
          manager_name VARCHAR(255) NOT NULL,
          contact_number VARCHAR(50) NOT NULL,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
      """
      
      // Create vehicle table
      val createVehicleTable = """
        CREATE TABLE IF NOT EXISTS vehicle (
          vehicle_id VARCHAR(50) PRIMARY KEY,
          fleet_id INT NOT NULL,
          vehicle_type VARCHAR(100) NOT NULL,
          model VARCHAR(100) NOT NULL,
          year INT NOT NULL,
          status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
          FOREIGN KEY (fleet_id) REFERENCES fleet(fleet_id)
        )
      """
      
      // Create driver table
      val createDriverTable = """
        CREATE TABLE IF NOT EXISTS driver (
          driver_id INT PRIMARY KEY AUTO_INCREMENT,
          vehicle_id VARCHAR(50) NOT NULL,
          name VARCHAR(255) NOT NULL,
          license_number VARCHAR(100) NOT NULL,
          contact VARCHAR(50) NOT NULL,
          FOREIGN KEY (vehicle_id) REFERENCES vehicle(vehicle_id)
        )
      """
      
      // Create fleet_daily_summary table
      val createFleetDailySummaryTable = """
        CREATE TABLE IF NOT EXISTS fleet_daily_summary (
          record_id VARCHAR(50) PRIMARY KEY,
          fleet_id INT NOT NULL,
          total_distance_km DECIMAL(10,2) NOT NULL,
          avg_speed DECIMAL(5,2) NOT NULL,
          fuel_consumed_liters DECIMAL(8,2) NOT NULL,
          anomaly_count INT NOT NULL DEFAULT 0,
          record_date DATE NOT NULL,
          generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY (fleet_id) REFERENCES fleet(fleet_id),
          UNIQUE KEY unique_fleet_date (fleet_id, record_date)
        )
      """
      
      val statement = connection.createStatement()
      statement.execute(createFleetTable)
      statement.execute(createVehicleTable)
      statement.execute(createDriverTable)
      statement.execute(createFleetDailySummaryTable)
      
      // Insert sample data if tables are empty
      insertSampleDataIfEmpty(connection)
      
      println("MySQL tables initialized successfully")
      
    } finally {
      connection.close()
    }
  }

  private def insertSampleDataIfEmpty(connection: Connection): Unit = {
    try {
      // Check if fleet table is empty
      val checkFleet = connection.prepareStatement("SELECT COUNT(*) FROM fleet")
      val fleetResult = checkFleet.executeQuery()
      fleetResult.next()
      val fleetCount = fleetResult.getInt(1)
      
      if (fleetCount == 0) {
        println("Inserting sample fleet and vehicle data...")
        
        // Insert sample fleets
        val insertFleet = connection.prepareStatement(
          "INSERT INTO fleet (fleet_id, fleet_name, city, manager_name, contact_number) VALUES (?, ?, ?, ?, ?)"
        )
        
        val fleets = List(
          (1, "Downtown Logistics", "San Francisco", "John Smith", "+1-555-0101"),
          (2, "Bay Area Express", "Oakland", "Sarah Johnson", "+1-555-0102"),
          (3, "Silicon Valley Transport", "San Jose", "Mike Chen", "+1-555-0103"),
          (4, "Golden Gate Delivery", "San Francisco", "Lisa Wong", "+1-555-0104"),
          (5, "Peninsula Freight", "Palo Alto", "David Rodriguez", "+1-555-0105")
        )
        
        fleets.foreach { case (id, name, city, manager, contact) =>
          insertFleet.setInt(1, id)
          insertFleet.setString(2, name)
          insertFleet.setString(3, city)
          insertFleet.setString(4, manager)
          insertFleet.setString(5, contact)
          insertFleet.executeUpdate()
        }
        
        // Insert sample vehicles
        val insertVehicle = connection.prepareStatement(
          "INSERT INTO vehicle (vehicle_id, fleet_id, vehicle_type, model, year, status) VALUES (?, ?, ?, ?, ?, ?)"
        )
        
        val vehicles = List(
          ("VHC-001-001", 1, "Truck", "Ford Transit", 2022, "ACTIVE"),
          ("VHC-001-002", 1, "Van", "Mercedes Sprinter", 2021, "ACTIVE"),
          ("VHC-001-003", 1, "Truck", "Isuzu NPR", 2023, "ACTIVE"),
          ("VHC-002-001", 2, "Truck", "Freightliner Cascadia", 2022, "ACTIVE"),
          ("VHC-002-002", 2, "Van", "Ford Transit", 2021, "ACTIVE"),
          ("VHC-002-003", 2, "Truck", "Volvo VNL", 2023, "ACTIVE"),
          ("VHC-003-001", 3, "Van", "Mercedes Sprinter", 2022, "ACTIVE"),
          ("VHC-003-002", 3, "Truck", "Peterbilt 579", 2021, "ACTIVE"),
          ("VHC-004-001", 4, "Van", "Ford Transit", 2023, "ACTIVE"),
          ("VHC-004-002", 4, "Truck", "Kenworth T680", 2022, "ACTIVE"),
          ("VHC-005-001", 5, "Truck", "Mack Anthem", 2021, "ACTIVE"),
          ("VHC-005-002", 5, "Van", "Mercedes Sprinter", 2023, "ACTIVE")
        )
        
        vehicles.foreach { case (id, fleetId, vehicleType, model, year, status) =>
          insertVehicle.setString(1, id)
          insertVehicle.setInt(2, fleetId)
          insertVehicle.setString(3, vehicleType)
          insertVehicle.setString(4, model)
          insertVehicle.setInt(5, year)
          insertVehicle.setString(6, status)
          insertVehicle.executeUpdate()
        }
        
        println(s"Inserted ${fleets.size} fleets and ${vehicles.size} vehicles")
      }
      
    } catch {
      case e: Exception =>
        println(s"Error inserting sample data: ${e.getMessage}")
    }
  }

  /**
   * Get all fleets
   */
  def getAllFleets(): List[Fleet] = {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("SELECT * FROM fleet")
      val resultSet = statement.executeQuery()
      
      val fleets = ListBuffer[Fleet]()
      while (resultSet.next()) {
        fleets += Fleet(
          fleet_id = resultSet.getInt("fleet_id"),
          fleet_name = resultSet.getString("fleet_name"),
          city = resultSet.getString("city"),
          manager_name = resultSet.getString("manager_name"),
          contact_number = resultSet.getString("contact_number"),
          created_at = resultSet.getTimestamp("created_at").toInstant
        )
      }
      
      fleets.toList
    } finally {
      connection.close()
    }
  }

  /**
   * Get all vehicles
   */
  def getAllVehicles(): List[Vehicle] = {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("SELECT * FROM vehicle")
      val resultSet = statement.executeQuery()
      
      val vehicles = ListBuffer[Vehicle]()
      while (resultSet.next()) {
        vehicles += Vehicle(
          vehicle_id = resultSet.getString("vehicle_id"),
          fleet_id = resultSet.getInt("fleet_id"),
          vehicle_type = resultSet.getString("vehicle_type"),
          model = resultSet.getString("model"),
          year = resultSet.getInt("year"),
          status = resultSet.getString("status")
        )
      }
      
      vehicles.toList
    } finally {
      connection.close()
    }
  }

  /**
   * Insert fleet daily summary
   */
  def insertFleetDailySummary(summary: FleetDailySummary): Try[Unit] = Try {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("""
        INSERT INTO fleet_daily_summary 
        (record_id, fleet_id, total_distance_km, avg_speed, fuel_consumed_liters, anomaly_count, record_date, generated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
        total_distance_km = VALUES(total_distance_km),
        avg_speed = VALUES(avg_speed),
        fuel_consumed_liters = VALUES(fuel_consumed_liters),
        anomaly_count = VALUES(anomaly_count),
        generated_at = VALUES(generated_at)
      """)
      
      statement.setString(1, summary.record_id)
      statement.setInt(2, summary.fleet_id)
      statement.setDouble(3, summary.total_distance_km)
      statement.setDouble(4, summary.avg_speed)
      statement.setDouble(5, summary.fuel_consumed_liters)
      statement.setInt(6, summary.anomaly_count)
      statement.setDate(7, java.sql.Date.valueOf(summary.record_date))
      statement.setTimestamp(8, Timestamp.from(summary.generated_at))
      
      statement.executeUpdate()
      
    } finally {
      connection.close()
    }
  }

  /**
   * Get fleet daily summaries for a date range
   */
  def getFleetDailySummaries(startDate: String, endDate: String): List[FleetDailySummary] = {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("""
        SELECT * FROM fleet_daily_summary 
        WHERE record_date BETWEEN ? AND ?
        ORDER BY record_date DESC, fleet_id
      """)
      
      statement.setDate(1, java.sql.Date.valueOf(startDate))
      statement.setDate(2, java.sql.Date.valueOf(endDate))
      
      val resultSet = statement.executeQuery()
      
      val summaries = ListBuffer[FleetDailySummary]()
      while (resultSet.next()) {
        summaries += FleetDailySummary(
          record_id = resultSet.getString("record_id"),
          fleet_id = resultSet.getInt("fleet_id"),
          total_distance_km = resultSet.getDouble("total_distance_km"),
          avg_speed = resultSet.getDouble("avg_speed"),
          fuel_consumed_liters = resultSet.getDouble("fuel_consumed_liters"),
          anomaly_count = resultSet.getInt("anomaly_count"),
          record_date = resultSet.getDate("record_date").toString,
          generated_at = resultSet.getTimestamp("generated_at").toInstant
        )
      }
      
      summaries.toList
    } finally {
      connection.close()
    }
  }


  def getVehiclesByFleet(fleetId: Int): List[Vehicle] = {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement(
        "SELECT * FROM vehicle WHERE fleet_id = ?"
      )
      statement.setInt(1, fleetId)
      val resultSet = statement.executeQuery()

      val vehicles = ListBuffer[Vehicle]()
      while (resultSet.next()) {
        vehicles += Vehicle(
          vehicle_id = resultSet.getString("vehicle_id"),
          fleet_id = resultSet.getInt("fleet_id"),
          vehicle_type = resultSet.getString("vehicle_type"),
          model = resultSet.getString("model"),
          year = resultSet.getInt("year"),
          status = resultSet.getString("status")
        )
      }

      vehicles.toList
    } finally {
      connection.close()
    }
  }


  /**
   * Get a single fleet by ID
   */
  def getFleet(fleetId: Int): Try[Option[Fleet]] = Try {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("SELECT * FROM fleet WHERE fleet_id = ?")
      statement.setInt(1, fleetId)
      val resultSet = statement.executeQuery()
      
      if (resultSet.next()) {
        Some(Fleet(
          fleet_id = resultSet.getInt("fleet_id"),
          fleet_name = resultSet.getString("fleet_name"),
          city = resultSet.getString("city"),
          manager_name = resultSet.getString("manager_name"),
          contact_number = resultSet.getString("contact_number"),
          created_at = resultSet.getTimestamp("created_at").toInstant
        ))
      } else {
        None
      }
    } finally {
      connection.close()
    }
  }

  /**
   * Get fleet daily summary for a specific fleet and date
   */
  def getFleetDailySummary(fleetId: Int, date: String): Try[Option[FleetDailySummary]] = Try {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("""
        SELECT * FROM fleet_daily_summary 
        WHERE fleet_id = ? AND record_date = ?
      """)
      statement.setInt(1, fleetId)
      statement.setDate(2, java.sql.Date.valueOf(date))
      
      val resultSet = statement.executeQuery()
      
      if (resultSet.next()) {
        Some(FleetDailySummary(
          record_id = resultSet.getString("record_id"),
          fleet_id = resultSet.getInt("fleet_id"),
          total_distance_km = resultSet.getDouble("total_distance_km"),
          avg_speed = resultSet.getDouble("avg_speed"),
          fuel_consumed_liters = resultSet.getDouble("fuel_consumed_liters"),
          anomaly_count = resultSet.getInt("anomaly_count"),
          record_date = resultSet.getDate("record_date").toString,
          generated_at = resultSet.getTimestamp("generated_at").toInstant
        ))
      } else {
        None
      }
    } finally {
      connection.close()
    }
  }

  /**
   * Get all fleets daily summary for a specific date
   */
  def getAllFleetsDailySummary(date: String): Try[List[FleetDailySummary]] = Try {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("""
        SELECT * FROM fleet_daily_summary 
        WHERE record_date = ?
        ORDER BY fleet_id
      """)
      statement.setDate(1, java.sql.Date.valueOf(date))
      
      val resultSet = statement.executeQuery()
      val summaries = scala.collection.mutable.ListBuffer[FleetDailySummary]()
      
      while (resultSet.next()) {
        summaries += FleetDailySummary(
          record_id = resultSet.getString("record_id"),
          fleet_id = resultSet.getInt("fleet_id"),
          total_distance_km = resultSet.getDouble("total_distance_km"),
          avg_speed = resultSet.getDouble("avg_speed"),
          fuel_consumed_liters = resultSet.getDouble("fuel_consumed_liters"),
          anomaly_count = resultSet.getInt("anomaly_count"),
          record_date = resultSet.getDate("record_date").toString,
          generated_at = resultSet.getTimestamp("generated_at").toInstant
        )
      }
      
      summaries.toList
    } finally {
      connection.close()
    }
  }

  /**
   * Health check
   */
  def healthCheck(): Try[Boolean] = Try {
    val connection = getConnection
    try {
      val statement = connection.prepareStatement("SELECT 1")
      val resultSet = statement.executeQuery()
      resultSet.next()
      resultSet.getInt(1) == 1
    } finally {
      connection.close()
    }
  }
}