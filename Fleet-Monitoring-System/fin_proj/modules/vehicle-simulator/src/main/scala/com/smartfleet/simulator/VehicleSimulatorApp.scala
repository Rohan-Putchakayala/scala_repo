package com.smartfleet.simulator

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, ActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import com.smartfleet.common.database.MySQLDatabase

object VehicleSimulatorApp {
  def main(args: Array[String]): Unit = {
    println("=== Vehicle Simulator Starting ===")
    val config = ConfigFactory.load()

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      println("Setting up Akka system...")
      val kafkaProducer: ActorRef[KafkaProducerActor.Command] =
        context.spawn(KafkaProducerActor(config), "kafka-producer")

      println("Connecting to MySQL database...")
      val mysql = new MySQLDatabase(config)
      
      val fleets = try {
        mysql.initializeTables()
        println("MySQL tables initialized successfully")
        val fleetData = mysql.getAllFleets()
        println(s"Found ${fleetData.size} fleets from MySQL")
        fleetData
      } catch {
        case e: Exception =>
          println(s"MySQL connection failed: ${e.getMessage}")
          println("Using default fleet configuration...")
          // Create default fleet data for testing
          import com.smartfleet.common.models.Fleet
          import java.time.Instant
          List(
            Fleet(1, "Test Fleet 1", "San Francisco", "Test Manager", "+1-555-0001", Instant.now()),
            Fleet(2, "Test Fleet 2", "Oakland", "Test Manager 2", "+1-555-0002", Instant.now())
          )
      }
      
      println(s"Using ${fleets.size} fleets")

      fleets.foreach { f =>
        println(s"Setting up fleet: ${f.fleet_name} (ID: ${f.fleet_id})")
        val fleetData = FleetSupervisor.FleetConfigData(
          fleetId = f.fleet_id,
          fleetName = f.fleet_name,
          city = f.city,
          vehicles = 0,
          operatingArea = FleetSupervisor.OperatingArea(
            centerLat = 0.0,
            centerLng = 0.0,
            radiusKm = 10.0
          )
        )

        val fleetActor =
          context.spawn(FleetSupervisor(fleetData, config, kafkaProducer), s"fleet-${f.fleet_id}")

        val vehicles = try {
          mysql.getVehiclesByFleet(f.fleet_id)
        } catch {
          case e: Exception =>
            println(s"  Could not load vehicles from MySQL: ${e.getMessage}")
            println(s"  Using default vehicles for fleet ${f.fleet_name}")
            // Create default vehicles for testing
            import com.smartfleet.common.models.Vehicle
            List(
              Vehicle(s"VHC-${f.fleet_id.toString.padTo(3, '0')}-001", f.fleet_id, "Truck", "Test Truck", 2022, "ACTIVE"),
              Vehicle(s"VHC-${f.fleet_id.toString.padTo(3, '0')}-002", f.fleet_id, "Van", "Test Van", 2021, "ACTIVE")
            )
        }
        println(s"  Found ${vehicles.size} vehicles for fleet ${f.fleet_name}")
        vehicles.foreach(v => fleetActor ! FleetSupervisor.AddVehicle(v.vehicle_id))

        // Start the fleet simulator AFTER vehicles are added
        fleetActor ! FleetSupervisor.StartFleet
        println(s"  Started fleet simulator for ${f.fleet_name}")
      }
      
      println("=== All fleet simulators started ===")
      println("Vehicle telemetry data will be generated every 15 seconds...")

      Behaviors.ignore
    }

    val system = ActorSystem[Nothing](rootBehavior, "VehicleSimulatorSystem")

    import scala.concurrent.Await
    import scala.concurrent.duration.Duration
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
