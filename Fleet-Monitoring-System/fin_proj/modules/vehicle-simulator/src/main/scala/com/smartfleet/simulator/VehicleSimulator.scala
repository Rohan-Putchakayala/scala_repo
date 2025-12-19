package com.smartfleet.simulator
//   75-85 → Timer triggers telemetry
//   200-220 → Creates telemetry event
//   342 -> Publishes to Kafka topic
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import com.smartfleet.common.models.{VehicleTelemetryEvent, JsonFormats}
import com.typesafe.config.Config
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import spray.json._
import scala.concurrent.duration._
import scala.util.Random
import java.time.Instant
import java.util.Properties
import scala.collection.mutable

/**
 * Vehicle simulator that generates telemetry events
 */
object VehicleSimulator {

  sealed trait Command
  case object StartTelemetry extends Command
  case object StopTelemetry extends Command
  case object SendTelemetry extends Command
  case class UpdateLocation(lat: Double, lng: Double) extends Command
  case class UpdateSpeed(speed: Double) extends Command
  private case object TelemetryTick extends Command

  def apply(
    vehicleId: String,
    fleetId: Int,
    fleetConfig: FleetConfig,
    kafkaProducer: ActorRef[KafkaProducerActor.Command]
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new VehicleSimulator(context, timers, vehicleId, fleetId, fleetConfig, kafkaProducer)
      }
    }
  }

  case class FleetConfig(
    centerLat: Double,
    centerLng: Double,
    radiusKm: Double,
    telemetryInterval: Int,
    anomalyPercentage: Double,
    speedLimits: SpeedLimits,
    fuelConfig: FuelConfig,
    engineTempConfig: EngineTempConfig,
    kafkaTopic: String
  )

  case class SpeedLimits(min: Double, max: Double, highway: (Double, Double), city: (Double, Double))
  case class FuelConfig(min: Double, max: Double, consumptionRate: Double)
  case class EngineTempConfig(normalRange: (Double, Double), max: Double, overheatThreshold: Double)
}

class VehicleSimulator(
  context: ActorContext[VehicleSimulator.Command],
  timers: TimerScheduler[VehicleSimulator.Command],
  vehicleId: String,
  fleetId: Int,
  fleetConfig: VehicleSimulator.FleetConfig,
  kafkaProducer: ActorRef[KafkaProducerActor.Command]
) extends AbstractBehavior[VehicleSimulator.Command](context) {

  import VehicleSimulator._
  import JsonFormats._

  private val random = new Random()

  // Vehicle state
  private var currentLat: Double = generateRandomLocation(fleetConfig.centerLat, fleetConfig.radiusKm)._1
  private var currentLng: Double = generateRandomLocation(fleetConfig.centerLng, fleetConfig.radiusKm)._2
  private var currentSpeed: Double = random.between(fleetConfig.speedLimits.city._1, fleetConfig.speedLimits.city._2)
  private var previousSpeed: Double = currentSpeed
  private var fuelLevel: Double = random.between(fleetConfig.fuelConfig.min, fleetConfig.fuelConfig.max)
  private var engineTemp: Double = random.between(fleetConfig.engineTempConfig.normalRange._1, fleetConfig.engineTempConfig.normalRange._2)
  private var deviceStatus: String = "OK"
  private var isMoving: Boolean = true
  private var tripDistance: Double = 0.0

  context.log.info(s"Vehicle $vehicleId initialized for fleet $fleetId")

  override def onMessage(msg: VehicleSimulator.Command): Behavior[VehicleSimulator.Command] = {
    msg match {
      case StartTelemetry =>
        context.log.info(s"Starting telemetry for vehicle $vehicleId")
        timers.startTimerWithFixedDelay(TelemetryTick, fleetConfig.telemetryInterval.seconds)
        this

      case StopTelemetry =>
        context.log.info(s"Stopping telemetry for vehicle $vehicleId")
        timers.cancel(TelemetryTick)
        this

      case TelemetryTick =>
        updateVehicleState()
        sendTelemetryEvent()
        this

      case SendTelemetry =>
        sendTelemetryEvent()
        this

      case UpdateLocation(lat, lng) =>
        currentLat = lat
        currentLng = lng
        this

      case UpdateSpeed(speed) =>
        previousSpeed = currentSpeed
        currentSpeed = speed
        this
    }
  }

  private def updateVehicleState(): Unit = {
    // Simulate realistic vehicle movement and behavior

    // Update location (simulate movement)
    if (isMoving && currentSpeed > 0) {
      val (newLat, newLng) = simulateMovement(currentLat, currentLng, currentSpeed)
      currentLat = newLat
      currentLng = newLng
    }

    // Update speed with realistic patterns
    updateSpeed()

    // Update fuel level
    updateFuelLevel()

    // Update engine temperature
    updateEngineTemperature()

    // Simulate device status changes
    updateDeviceStatus()

    // Simulate trip patterns (start/stop)
    updateMovementState()
  }

  private def simulateMovement(lat: Double, lng: Double, speedKmh: Double): (Double, Double) = {
    // Convert speed to distance moved in telemetry interval
    val distanceKm = (speedKmh * fleetConfig.telemetryInterval) / 3600.0
    tripDistance += distanceKm

    // Random direction with some continuity (vehicles don't change direction randomly)
    val bearing = random.nextDouble() * 360

    // Simple movement calculation (in reality, this would follow roads)
    val earthRadius = 6371.0 // km
    val deltaLat = (distanceKm / earthRadius) * math.cos(math.toRadians(bearing))
    val deltaLng = (distanceKm / earthRadius) * math.sin(math.toRadians(bearing)) / math.cos(math.toRadians(lat))

    val newLat = lat + math.toDegrees(deltaLat)
    val newLng = lng + math.toDegrees(deltaLng)

    // Keep within operating area
    val maxDistance = calculateDistance(fleetConfig.centerLat, fleetConfig.centerLng, newLat, newLng)
    if (maxDistance > fleetConfig.radiusKm) {
      // Return towards center
      val bearingToCenter = calculateBearing(newLat, newLng, fleetConfig.centerLat, fleetConfig.centerLng)
      val adjustedLat = lat + math.toDegrees(deltaLat * math.cos(math.toRadians(bearingToCenter)))
      val adjustedLng = lng + math.toDegrees(deltaLng * math.sin(math.toRadians(bearingToCenter)))
      (adjustedLat, adjustedLng)
    } else {
      (newLat, newLng)
    }
  }

  private def updateSpeed(): Unit = {
    previousSpeed = currentSpeed

    if (!isMoving) {
      currentSpeed = 0.0
      return
    }

    // Simulate realistic speed changes
    val speedChange = random.nextGaussian() * 5.0 // km/h variance
    val newSpeed = currentSpeed + speedChange

    // Apply constraints
    val constrainedSpeed = math.max(0.0, math.min(fleetConfig.speedLimits.max, newSpeed))

    // Simulate different driving patterns
    val timeOfDay = Instant.now().getEpochSecond % 86400 // seconds in day
    val isPeakHours = (timeOfDay >= 28800 && timeOfDay <= 32400) || (timeOfDay >= 61200 && timeOfDay <= 68400) // 8-9 AM, 5-7 PM

    currentSpeed = if (isPeakHours) {
      // Slower speeds during peak hours
      math.min(constrainedSpeed, fleetConfig.speedLimits.city._2)
    } else {
      // Mix of city and highway speeds
      if (random.nextDouble() < 0.3) {
        // Highway driving
        random.between(fleetConfig.speedLimits.highway._1, fleetConfig.speedLimits.highway._2)
      } else {
        // City driving
        math.min(constrainedSpeed, fleetConfig.speedLimits.city._2)
      }
    }
  }

  private def updateFuelLevel(): Unit = {
    if (isMoving && currentSpeed > 0) {
      // Consume fuel based on speed and distance
      val consumption = (currentSpeed * fleetConfig.telemetryInterval / 3600.0) * fleetConfig.fuelConfig.consumptionRate
      fuelLevel = math.max(0.0, fuelLevel - consumption)

      // Simulate refueling
      if (fuelLevel < 15.0 && random.nextDouble() < 0.1) {
        fuelLevel = random.between(80.0, 100.0)
      }
    }
  }

  private def updateEngineTemperature(): Unit = {
    val baseTemp = fleetConfig.engineTempConfig.normalRange._1
    val maxTemp = fleetConfig.engineTempConfig.normalRange._2

    // Temperature increases with speed and load
    val speedFactor = currentSpeed / fleetConfig.speedLimits.max
    val targetTemp = baseTemp + (maxTemp - baseTemp) * speedFactor

    // Add some randomness
    val tempChange = (targetTemp - engineTemp) * 0.1 + random.nextGaussian() * 1.0
    engineTemp = math.max(baseTemp, math.min(fleetConfig.engineTempConfig.max, engineTemp + tempChange))
  }

  private def updateDeviceStatus(): Unit = {
    // Simulate device issues
    val errorProbability = 0.001 // 0.1% chance per tick
    val warningProbability = 0.005 // 0.5% chance per tick

    if (deviceStatus == "ERROR" && random.nextDouble() < 0.1) {
      deviceStatus = "OK" // Recover from error
    } else if (deviceStatus == "WARNING" && random.nextDouble() < 0.2) {
      deviceStatus = "OK" // Recover from warning
    } else if (deviceStatus == "OK") {
      val rand = random.nextDouble()
      if (rand < errorProbability) {
        deviceStatus = "ERROR"
      } else if (rand < errorProbability + warningProbability) {
        deviceStatus = "WARNING"
      }
    }
  }

  private def updateMovementState(): Unit = {
    // Simulate realistic stop/start patterns
    if (isMoving && random.nextDouble() < 0.02) { // 2% chance to stop
      isMoving = false
      currentSpeed = 0.0
    } else if (!isMoving && random.nextDouble() < 0.3) { // 30% chance to start moving
      isMoving = true
    }
  }

  private def sendTelemetryEvent(): Unit = {
    val event = VehicleTelemetryEvent(
      vehicle_id = vehicleId,
      fleet_id = fleetId,
      speed_kmh = math.round(currentSpeed * 10.0) / 10.0,
      fuel_level_percent = math.round(fuelLevel * 10.0) / 10.0,
      engine_temp_c = math.round(engineTemp * 10.0) / 10.0,
      gps_lat = currentLat,
      gps_long = currentLng,
      device_status = deviceStatus,
      timestamp = Instant.now().toEpochMilli
    )

    val eventJson = event.toJson.compactPrint

    if(vehicleId == "VEH-0129") {

      // Log telemetry generation at vehicle level
      println(s"🚗 [VEHICLE-$vehicleId] Generating telemetry for Fleet $fleetId")
      println(s"   📍 Location: (${currentLat}, ${currentLng})")
      println(s"   🏃 Speed: ${event.speed_kmh}km/h | Moving: $isMoving")
      println(s"   ⛽ Fuel: ${event.fuel_level_percent}% | 🌡️ Engine: ${event.engine_temp_c}°C")
      println(s"   📱 Device: $deviceStatus | 📏 Trip Distance: ${math.round(tripDistance * 100.0) / 100.0}km")
    }

    kafkaProducer ! KafkaProducerActor.SendMessage(fleetConfig.kafkaTopic, vehicleId, eventJson)
  }

  private def calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = {
    val R = 6371.0 // Earth's radius in km
    val dLat = math.toRadians(lat2 - lat1)
    val dLon = math.toRadians(lon2 - lon1)
    val a = math.sin(dLat / 2) * math.sin(dLat / 2) +
      math.cos(math.toRadians(lat1)) * math.cos(math.toRadians(lat2)) *
      math.sin(dLon / 2) * math.sin(dLon / 2)
    val c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    R * c
  }

  private def calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = {
    val dLon = math.toRadians(lon2 - lon1)
    val y = math.sin(dLon) * math.cos(math.toRadians(lat2))
    val x = math.cos(math.toRadians(lat1)) * math.sin(math.toRadians(lat2)) -
      math.sin(math.toRadians(lat1)) * math.cos(math.toRadians(lat2)) * math.cos(dLon)
    math.toDegrees(math.atan2(y, x))
  }

  private def generateRandomLocation(center: Double, radiusKm: Double): (Double, Double) = {
    val angle = random.nextDouble() * 2 * math.Pi
    val distance = random.nextDouble() * radiusKm

    val deltaLat = distance * math.cos(angle) / 111.0 // Rough conversion
    val deltaLng = distance * math.sin(angle) / (111.0 * math.cos(math.toRadians(center)))

    (center + deltaLat, center + deltaLng)
  }
}

/**
 * Kafka Producer Actor for sending telemetry events
 */
object KafkaProducerActor {
  sealed trait Command
  case class SendMessage(topic: String, key: String, value: String) extends Command
  case object Close extends Command

  def apply(config: Config): Behavior[Command] = {
    Behaviors.setup { context =>
      val props = new Properties()
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getString("kafka.bootstrap-servers"))
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, config.getString("kafka.producer.key-serializer"))
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, config.getString("kafka.producer.value-serializer"))
      props.put(ProducerConfig.ACKS_CONFIG, config.getString("kafka.producer.acks"))
      props.put(ProducerConfig.RETRIES_CONFIG, config.getInt("kafka.producer.retries"))
      props.put(ProducerConfig.BATCH_SIZE_CONFIG, config.getInt("kafka.producer.batch-size"))
      props.put(ProducerConfig.LINGER_MS_CONFIG, config.getInt("kafka.producer.linger-ms"))
      props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, config.getLong("kafka.producer.buffer-memory"))
      props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.getString("kafka.producer.compression-type"))

      val producer = new KafkaProducer[String, String](props)

      new KafkaProducerActor(context, producer)
    }
  }
}

class KafkaProducerActor(
  context: ActorContext[KafkaProducerActor.Command],
  producer: KafkaProducer[String, String]
) extends AbstractBehavior[KafkaProducerActor.Command](context) {

  import KafkaProducerActor._

  override def onMessage(msg: KafkaProducerActor.Command): Behavior[KafkaProducerActor.Command] = {
    msg match {
      case SendMessage(topic, key, value) =>
        try {
          val record = new ProducerRecord[String, String](topic, key, value)
          producer.send(record, (metadata, exception) => {
            if (exception != null) {
              context.log.error(s"Failed to send message to topic $topic", exception)
            }
          })
        } catch {
          case e: Exception =>
            context.log.error(s"Error sending message to topic $topic", e)
        }
        this

      case Close =>
        producer.close()
        Behaviors.stopped
    }
  }
}

/**
 * Fleet Supervisor that manages all vehicles in a fleet
 */
object FleetSupervisor {
  sealed trait Command
  case object StartFleet extends Command
  case object StopFleet extends Command
  case class AddVehicle(vehicleId: String) extends Command
  case class RemoveVehicle(vehicleId: String) extends Command

  case class FleetConfigData(
    fleetId: Int,
    fleetName: String,
    city: String,
    vehicles: Int,
    operatingArea: OperatingArea
  )

  case class OperatingArea(centerLat: Double, centerLng: Double, radiusKm: Double)

  def apply(
    fleetConfig: FleetConfigData,
    simulatorConfig: Config,
    kafkaProducer: ActorRef[KafkaProducerActor.Command]
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      new FleetSupervisor(context, fleetConfig, simulatorConfig, kafkaProducer)
    }
  }
}

class FleetSupervisor(
  context: ActorContext[FleetSupervisor.Command],
  fleetConfigData: FleetSupervisor.FleetConfigData,
  simulatorConfig: Config,
  kafkaProducer: ActorRef[KafkaProducerActor.Command]
) extends AbstractBehavior[FleetSupervisor.Command](context) {

  import FleetSupervisor._
  import VehicleSimulator._

  private val vehicles = mutable.Map[String, ActorRef[VehicleSimulator.Command]]()

  // Convert config to FleetConfig
  private val fleetConfig = FleetConfig(
    centerLat = fleetConfigData.operatingArea.centerLat,
    centerLng = fleetConfigData.operatingArea.centerLng,
    radiusKm = fleetConfigData.operatingArea.radiusKm,
    telemetryInterval = if (simulatorConfig.hasPath("simulator.telemetry-interval")) simulatorConfig.getInt("simulator.telemetry-interval") else 15,
    anomalyPercentage = simulatorConfig.getDouble("simulator.anomaly-percentage"),
    speedLimits = SpeedLimits(
      min = simulatorConfig.getDouble("simulator.speed.min"),
      max = simulatorConfig.getDouble("simulator.speed.max"),
      highway = (
        simulatorConfig.getDoubleList("simulator.speed.highway-range").get(0),
        simulatorConfig.getDoubleList("simulator.speed.highway-range").get(1)
      ),
      city = (
        simulatorConfig.getDoubleList("simulator.speed.city-range").get(0),
        simulatorConfig.getDoubleList("simulator.speed.city-range").get(1)
      )
    ),
    fuelConfig = FuelConfig(
      min = simulatorConfig.getDouble("simulator.fuel.min"),
      max = simulatorConfig.getDouble("simulator.fuel.max"),
      consumptionRate = simulatorConfig.getDouble("simulator.fuel.consumption-rate")
    ),
    engineTempConfig = EngineTempConfig(
      normalRange = (
        simulatorConfig.getDoubleList("simulator.engine-temp.normal-range").get(0),
        simulatorConfig.getDoubleList("simulator.engine-temp.normal-range").get(1)
      ),
      max = simulatorConfig.getDouble("simulator.engine-temp.max"),
      overheatThreshold = simulatorConfig.getDouble("simulator.engine-temp.overheat-threshold")
    ),
    kafkaTopic = simulatorConfig.getString("kafka.topics.telemetry")
  )

  context.log.info(s"Fleet supervisor started for fleet ${fleetConfigData.fleetId} with ${fleetConfigData.vehicles} vehicles")

  // Initialize vehicles
  initializeVehicles()

  override def onMessage(msg: FleetSupervisor.Command): Behavior[FleetSupervisor.Command] = {
    msg match {
      case StartFleet =>
        context.log.info(s"Starting all vehicles in fleet ${fleetConfigData.fleetId}")
        vehicles.values.foreach(_ ! VehicleSimulator.StartTelemetry)
        this

      case StopFleet =>
        context.log.info(s"Stopping all vehicles in fleet ${fleetConfigData.fleetId}")
        vehicles.values.foreach(_ ! VehicleSimulator.StopTelemetry)
        this

      case AddVehicle(vehicleId) =>
        if (!vehicles.contains(vehicleId)) {
          val vehicleActor = context.spawn(
            Behaviors.supervise(VehicleSimulator(vehicleId, fleetConfigData.fleetId, fleetConfig, kafkaProducer))
              .onFailure[Exception](SupervisorStrategy.restart),
            s"vehicle-$vehicleId"
          )
          vehicles += vehicleId -> vehicleActor
          context.log.info(s"Added vehicle $vehicleId to fleet ${fleetConfigData.fleetId}")
        }
        this

      case RemoveVehicle(vehicleId) =>
        vehicles.get(vehicleId).foreach { actor =>
          actor ! VehicleSimulator.StopTelemetry
          context.stop(actor)
          vehicles -= vehicleId
          context.log.info(s"Removed vehicle $vehicleId from fleet ${fleetConfigData.fleetId}")
        }
        this
    }
  }

  private def initializeVehicles(): Unit = {
    for (i <- 1 to fleetConfigData.vehicles) {
      val vehicleId = f"VHC-${fleetConfigData.fleetId}%03d-$i%03d"
      val vehicleActor = context.spawn(
        Behaviors.supervise(VehicleSimulator(vehicleId, fleetConfigData.fleetId, fleetConfig, kafkaProducer))
          .onFailure[Exception](SupervisorStrategy.restart),
        s"vehicle-$vehicleId"
      )
      vehicles += vehicleId -> vehicleActor
    }
  }
}
