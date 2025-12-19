package com.smartfleet.common.utils

import com.smartfleet.common.models.{EnrichedTelemetryEvent, DailyAvgSpeedByFleet, DailyFuelConsumptionByFleet, DailyAnomalyCounts}
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import scala.jdk.CollectionConverters._

/**
 * Production-ready protobuf-like encoder and decoder for telemetry events.
 * Fully safe, correct wire types, correct varints, bounded memory, correct repeated fields.
 */
object ProtobufConverter {

  // ------------------------------
  // WRITE HELPERS
  // ------------------------------
  private def writeVarint(out: ByteArrayOutputStream, v0: Long): Unit = {
    var v = v0
    while ((v & ~0x7FL) != 0L) {
      out.write(((v & 0x7F) | 0x80).toInt)
      v >>>= 7
    }
    out.write((v & 0x7F).toInt)
  }

  private def writeTag(out: ByteArrayOutputStream, fieldNum: Int, wireType: Int): Unit =
    writeVarint(out, ((fieldNum << 3) | wireType).toLong)

  private def writeString(out: ByteArrayOutputStream, fieldNum: Int, value: String): Unit = {
    val bytes = value.getBytes("UTF-8")
    writeTag(out, fieldNum, 2)
    writeVarint(out, bytes.length)
    out.write(bytes)
  }

  private def writeFloat(out: ByteArrayOutputStream, fieldNum: Int, value: Float): Unit = {
    writeTag(out, fieldNum, 5)
    val bb = ByteBuffer.allocate(4)
    bb.putFloat(value)
    out.write(bb.array())
  }

  private def writeDouble(out: ByteArrayOutputStream, fieldNum: Int, value: Double): Unit = {
    writeTag(out, fieldNum, 1)
    val bb = ByteBuffer.allocate(8)
    bb.putDouble(value)
    out.write(bb.array())
  }

  private def writeInt32(out: ByteArrayOutputStream, fieldNum: Int, value: Int): Unit = {
    writeTag(out, fieldNum, 0)
    writeVarint(out, value & 0xffffffffL)
  }

  private def writeInt64(out: ByteArrayOutputStream, fieldNum: Int, value: Long): Unit = {
    writeTag(out, fieldNum, 0)
    writeVarint(out, value)
  }

  private def writeBool(out: ByteArrayOutputStream, fieldNum: Int, value: Boolean): Unit = {
    writeTag(out, fieldNum, 0)
    writeVarint(out, if (value) 1 else 0)
  }

  private def writeBytes(out: ByteArrayOutputStream, fieldNum: Int, bytes: Array[Byte]): Unit = {
    writeTag(out, fieldNum, 2)
    writeVarint(out, bytes.length)
    out.write(bytes)
  }

  // ------------------------------
  // EVENT → BYTES
  // ------------------------------
  def toProtobufBytes(e: EnrichedTelemetryEvent): Array[Byte] = {
    val out = new ByteArrayOutputStream()

    writeString(out, 1, e.reading_id)
    writeString(out, 2, e.vehicle_id)
    writeInt32(out, 3, e.fleet_id)
    writeFloat(out, 4, e.speed_kmh.toFloat)
    writeFloat(out, 5, e.fuel_level_percent.toFloat)
    writeFloat(out, 6, e.engine_temp_c.toFloat)
    writeDouble(out, 7, e.gps_lat)
    writeDouble(out, 8, e.gps_long)
    writeBool(out, 9, e.is_anomaly)
    writeInt64(out, 10, e.timestamp)
    writeInt64(out, 11, e.ingestion_timestamp)
    writeString(out, 12, e.device_status)

    e.anomaly_type.foreach(t => writeString(out, 13, t))

    out.toByteArray
  }

  // ------------------------------
  // BATCH → BYTES
  // ------------------------------
  def toBatchProtobufBytes(events: List[EnrichedTelemetryEvent], batchId: String): Array[Byte] = {
    val out = new ByteArrayOutputStream()

    writeString(out, 1, batchId)
    writeInt64(out, 2, System.currentTimeMillis())
    writeInt32(out, 3, events.size)
    writeInt32(out, 4, events.count(_.is_anomaly))

    // ✔ REPEATED CORRECT FIELD NUMBER = 5
    events.foreach { e =>
      val eventBytes = toProtobufBytes(e)
      writeBytes(out, 5, eventBytes)
    }

    out.toByteArray
  }

  // ------------------------------
  // READ HELPERS
  // ------------------------------
  private val MAX_ALLOWED_BYTES = 10 * 1024 * 1024 // 10MB safety limit

  private def readVarint(buf: ByteBuffer): Long = {
    var shift = 0
    var result = 0L
    while (true) {
      if (!buf.hasRemaining()) throw new IllegalArgumentException("Unexpected end of buffer")
      val b = buf.get()
      result |= ((b & 0x7F).toLong << shift)
      if ((b & 0x80) == 0) return result
      shift += 7
      if (shift > 63) throw new IllegalArgumentException("Varint too long")
    }
    0L
  }

  private def readString(buf: ByteBuffer): String = {
    val len = readVarint(buf).toInt
    if (len < 0 || len > MAX_ALLOWED_BYTES) throw new IllegalArgumentException(s"Invalid string length: $len")
    val arr = new Array[Byte](len)
    buf.get(arr)
    new String(arr, "UTF-8")
  }

  private def readBytes(buf: ByteBuffer): Array[Byte] = {
    val len = readVarint(buf).toInt
    if (len < 0 || len > MAX_ALLOWED_BYTES) throw new IllegalArgumentException(s"Invalid bytes length: $len")
    val arr = new Array[Byte](len)
    buf.get(arr)
    arr
  }

  private def skipField(buf: ByteBuffer, wireType: Int): Unit = wireType match {
    case 0 => readVarint(buf)
    case 1 => buf.position(buf.position() + 8)
    case 2 =>
      val len = readVarint(buf).toInt
      buf.position(buf.position() + len)
    case 5 => buf.position(buf.position() + 4)
    case _ => buf.position(buf.limit()) // hard skip
  }

  // ------------------------------
  // EVENT PARSE
  // ------------------------------
  def fromProtobufBytes(bytes: Array[Byte]): Option[EnrichedTelemetryEvent] = {
    try {
      val buf = ByteBuffer.wrap(bytes)

      var readingId = ""
      var vehicleId = ""
      var fleetId = 0
      var speed = 0.0
      var fuel = 0.0
      var temp = 0.0
      var lat = 0.0
      var lon = 0.0
      var anomaly = false
      var ts = 0L
      var ingTs = 0L
      var status = ""
      var anomalyType: Option[String] = None

      while (buf.hasRemaining()) {
        val tag = readVarint(buf).toInt
        val fieldNum = tag >>> 3
        val wireType = tag & 7

        fieldNum match {
          case 1 => readingId = readString(buf)
          case 2 => vehicleId = readString(buf)
          case 3 => fleetId = readVarint(buf).toInt
          case 4 => speed = buf.getFloat().toDouble
          case 5 => fuel = buf.getFloat().toDouble
          case 6 => temp = buf.getFloat().toDouble
          case 7 => lat = buf.getDouble()
          case 8 => lon = buf.getDouble()
          case 9 => anomaly = readVarint(buf) != 0
          case 10 => ts = readVarint(buf)
          case 11 => ingTs = readVarint(buf)
          case 12 => status = readString(buf)
          case 13 => anomalyType = Some(readString(buf))
          case _ => skipField(buf, wireType)
        }
      }

      Some(
        EnrichedTelemetryEvent(
          reading_id = readingId,
          vehicle_id = vehicleId,
          fleet_id = fleetId,
          speed_kmh = speed,
          fuel_level_percent = fuel,
          engine_temp_c = temp,
          gps_lat = lat,
          gps_long = lon,
          device_status = status,
          timestamp = ts,
          ingestion_timestamp = ingTs,
          is_anomaly = anomaly,
          anomaly_type = anomalyType
        )
      )
    } catch {
      case e: Exception =>
        println(s"[Protobuf] Error parsing event: ${e.getMessage}")
        None
    }
  }

  // ------------------------------
  // BATCH PARSE
  // ------------------------------
  def fromBatchProtobufBytes(bytes: Array[Byte]): List[EnrichedTelemetryEvent] = {
    try {
      val buf = ByteBuffer.wrap(bytes)
      val events = scala.collection.mutable.ListBuffer[EnrichedTelemetryEvent]()

      while (buf.hasRemaining()) {
        val tag = readVarint(buf).toInt
        val fieldNum = tag >>> 3
        val wireType = tag & 7

        fieldNum match {
          case 1 => skipField(buf, wireType) // batch_id
          case 2 => skipField(buf, wireType) // batch_timestamp
          case 3 => skipField(buf, wireType) // event_count
          case 4 => skipField(buf, wireType) // anomaly_count

          case 5 => // repeated event
            val evBytes = readBytes(buf)
            fromProtobufBytes(evBytes).foreach(events += _)

          case _ => skipField(buf, wireType)
        }
      }

      events.toList
    } catch {
      case e: Exception =>
        println(s"🚨 CRITICAL: Error parsing batch protobuf bytes: ${e.getMessage}")
        println(s"🚨 Bytes length: ${bytes.length}, Exception: ${e.getClass.getSimpleName}")
        List.empty[EnrichedTelemetryEvent]
    }
  }

  // ===============================
  // PIPELINE4 DASHBOARD REPORTS
  // ===============================

  /**
   * Convert DailyAvgSpeedByFleet list to protobuf bytes
   */
  def toAvgSpeedPB(items: List[DailyAvgSpeedByFleet]): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    
    // Write list header
    writeInt32(out, 1, items.size) // item count
    
    // Write each item
    items.zipWithIndex.foreach { case (item, index) =>
      val itemOut = new ByteArrayOutputStream()
      
      writeInt32(itemOut, 1, item.fleet_id)
      writeString(itemOut, 2, item.fleet_name)
      writeString(itemOut, 3, item.date)
      writeDouble(itemOut, 4, item.avg_speed)
      writeDouble(itemOut, 5, item.max_speed)
      writeDouble(itemOut, 6, item.min_speed)
      writeInt32(itemOut, 7, item.vehicle_count)
      writeInt64(itemOut, 8, item.total_readings)
      
      // Write item as bytes field
      writeBytes(out, 2, itemOut.toByteArray)
    }
    
    out.toByteArray
  }

  /**
   * Convert DailyFuelConsumptionByFleet list to protobuf bytes
   */
  def toFuelPB(items: List[DailyFuelConsumptionByFleet]): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    
    // Write list header
    writeInt32(out, 1, items.size) // item count
    
    // Write each item
    items.foreach { item =>
      val itemOut = new ByteArrayOutputStream()
      
      writeInt32(itemOut, 1, item.fleet_id)
      writeString(itemOut, 2, item.fleet_name)
      writeString(itemOut, 3, item.date)
      writeDouble(itemOut, 4, item.total_fuel_consumed)
      writeDouble(itemOut, 5, item.avg_fuel_efficiency)
      writeDouble(itemOut, 6, item.total_distance)
      writeInt32(itemOut, 7, item.vehicle_count)
      writeDouble(itemOut, 8, item.avg_fuel_level)
      
      // Write item as bytes field
      writeBytes(out, 2, itemOut.toByteArray)
    }
    
    out.toByteArray
  }

  /**
   * Convert DailyAnomalyCounts list to protobuf bytes
   */
  def toAnomalyPB(items: List[DailyAnomalyCounts]): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    
    // Write list header
    writeInt32(out, 1, items.size) // item count
    
    // Write each item
    items.foreach { item =>
      val itemOut = new ByteArrayOutputStream()
      
      writeInt32(itemOut, 1, item.fleet_id)
      writeString(itemOut, 2, item.fleet_name)
      writeString(itemOut, 3, item.date)
      writeInt32(itemOut, 4, item.total_anomalies)
      writeInt32(itemOut, 5, item.speeding_count)
      writeInt32(itemOut, 6, item.engine_overheat_count)
      writeInt32(itemOut, 7, item.sudden_stop_count)
      writeInt32(itemOut, 8, item.low_fuel_count)
      writeInt32(itemOut, 9, item.device_error_count)
      writeDouble(itemOut, 10, item.anomaly_rate)
      writeInt64(itemOut, 11, item.total_readings)
      
      // Write item as bytes field
      writeBytes(out, 2, itemOut.toByteArray)
    }
    
    out.toByteArray
  }

  /**
   * Read protobuf data for a specific date from S3
   */
  def readProtobufDataForDate(
    s3Client: software.amazon.awssdk.services.s3.S3Client, 
    bucket: String, 
    pathPrefix: String, 
    date: String
  ): List[EnrichedTelemetryEvent] = {
    
    val datePrefix = s"$pathPrefix/date=$date/"
    
    try {
      // List all objects for the date
      val listRequest = software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(datePrefix)
        .build()
      
      val objects = s3Client.listObjectsV2(listRequest).contents().asScala.toList
        .filter(_.key().endsWith(".pb"))  // Only protobuf files
      
      println(s"Found ${objects.size} protobuf files for date: $date")
      
      if (objects.isEmpty) {
        println(s"No protobuf files found in S3 path: $datePrefix")
        println("Available S3 objects in telemetry/enriched/:")
        
        // List what's actually available for debugging
        val debugListRequest = software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
          .bucket(bucket)
          .prefix("telemetry/enriched/")
          .build()
        
        val debugObjects = s3Client.listObjectsV2(debugListRequest).contents().asScala.take(10)
        debugObjects.foreach(obj => println(s"  - ${obj.key()}"))
        
        return List.empty[EnrichedTelemetryEvent]
      }
      
      // Read and parse each protobuf file
      val allEvents = objects.flatMap { obj =>
        try {
          println(s"Reading protobuf file: ${obj.key()}")
          val getRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
            .bucket(bucket)
            .key(obj.key())
            .build()
          
          val inputStream = s3Client.getObject(getRequest)
          val bytes = inputStream.readAllBytes()
          inputStream.close()
          
          // Parse protobuf bytes back to events
          val events = ProtobufConverter.fromBatchProtobufBytes(bytes)
          println(s"  Loaded ${events.size} events from ${obj.key()}")
          events
        } catch {
          case e: Exception =>
            println(s"Error reading protobuf file ${obj.key()}: ${e.getMessage}")
            List.empty[EnrichedTelemetryEvent]
        }
      }
      
      println(s"Loaded ${allEvents.size} telemetry events for date: $date")
      allEvents
      
    } catch {
      case e: Exception =>
        println(s"Error listing S3 objects for date $date: ${e.getMessage}")
        List.empty[EnrichedTelemetryEvent]
    }
  }
}