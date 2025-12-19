package com.smartfleet.common.utils

import com.smartfleet.common.models.EnrichedTelemetryEvent
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.util.UUID

class ProtobufConverterTest extends AnyFlatSpec with Matchers {

  "ProtobufConverter" should "round-trip serialize and deserialize single events" in {
    val event = createTestEvent()
    
    val bytes = ProtobufConverter.toProtobufBytes(event)
    val parsed = ProtobufConverter.fromProtobufBytes(bytes)
    
    parsed shouldBe defined
    val parsedEvent = parsed.get
    
    parsedEvent.reading_id shouldBe event.reading_id
    parsedEvent.vehicle_id shouldBe event.vehicle_id
    parsedEvent.fleet_id shouldBe event.fleet_id
    // Use approximate equality for floating point values due to float/double conversion precision
    parsedEvent.speed_kmh shouldBe event.speed_kmh +- 0.001
    parsedEvent.fuel_level_percent shouldBe event.fuel_level_percent +- 0.001
    parsedEvent.engine_temp_c shouldBe event.engine_temp_c +- 0.001
    parsedEvent.gps_lat shouldBe event.gps_lat +- 0.000001
    parsedEvent.gps_long shouldBe event.gps_long +- 0.000001
    parsedEvent.device_status shouldBe event.device_status
    parsedEvent.timestamp shouldBe event.timestamp
    parsedEvent.ingestion_timestamp shouldBe event.ingestion_timestamp
    parsedEvent.is_anomaly shouldBe event.is_anomaly
    parsedEvent.anomaly_type shouldBe event.anomaly_type
  }

  it should "round-trip serialize and deserialize batch events" in {
    val events = (1 to 50).map(_ => createTestEvent()).toList
    val batchId = "test-batch-123"
    
    val bytes = ProtobufConverter.toBatchProtobufBytes(events, batchId)
    val parsed = ProtobufConverter.fromBatchProtobufBytes(bytes)
    
    parsed.size shouldBe events.size
    
    // Check first and last events to verify order preservation
    val firstParsed = parsed.head
    val firstOriginal = events.head
    firstParsed.reading_id shouldBe firstOriginal.reading_id
    firstParsed.vehicle_id shouldBe firstOriginal.vehicle_id
    firstParsed.is_anomaly shouldBe firstOriginal.is_anomaly
    
    val lastParsed = parsed.last
    val lastOriginal = events.last
    lastParsed.reading_id shouldBe lastOriginal.reading_id
    lastParsed.vehicle_id shouldBe lastOriginal.vehicle_id
    lastParsed.is_anomaly shouldBe lastOriginal.is_anomaly
  }

  it should "handle boolean values correctly" in {
    val anomalyEvent = createTestEvent(isAnomaly = true, anomalyType = Some("SPEEDING"))
    val normalEvent = createTestEvent(isAnomaly = false, anomalyType = None)
    
    val events = List(anomalyEvent, normalEvent)
    val bytes = ProtobufConverter.toBatchProtobufBytes(events, "bool-test")
    val parsed = ProtobufConverter.fromBatchProtobufBytes(bytes)
    
    parsed.size shouldBe 2
    parsed.head.is_anomaly shouldBe true
    parsed.head.anomaly_type shouldBe Some("SPEEDING")
    parsed.last.is_anomaly shouldBe false
    parsed.last.anomaly_type shouldBe None
  }

  it should "handle large batches without memory issues" in {
    val events = (1 to 1000).map(_ => createTestEvent()).toList
    val batchId = "large-batch-test"
    
    val bytes = ProtobufConverter.toBatchProtobufBytes(events, batchId)
    bytes.length should be > 0
    bytes.length should be < (10 * 1024 * 1024) // Should be less than 10MB
    
    val parsed = ProtobufConverter.fromBatchProtobufBytes(bytes)
    parsed.size shouldBe events.size
  }

  it should "handle corrupted data gracefully" in {
    val corruptedBytes = Array[Byte](1, 2, 3, 4, 5, -1, -2, -3)
    
    val parsed = ProtobufConverter.fromBatchProtobufBytes(corruptedBytes)
    parsed shouldBe empty
  }

  it should "reject oversized length fields" in {
    // Create a buffer with a huge length field that would cause OOM
    val maliciousBytes = Array[Byte](
      0x08, 0x01, // field 1, varint, value 1
      0x12, 0xFF.toByte, 0xFF.toByte, 0xFF.toByte, 0x7F // field 2, length-delimited, huge length
    )
    
    val parsed = ProtobufConverter.fromBatchProtobufBytes(maliciousBytes)
    parsed shouldBe empty
  }

  it should "handle unknown wire types gracefully" in {
    // Create a buffer with an unknown wire type (7 is not valid)
    val unknownWireTypeBytes = Array[Byte](
      0x08, 0x01, // field 1, varint, value 1
      0x3F // field 7, wire type 7 (invalid)
    )
    
    val parsed = ProtobufConverter.fromBatchProtobufBytes(unknownWireTypeBytes)
    parsed shouldBe empty
  }

  private def createTestEvent(
    isAnomaly: Boolean = false, 
    anomalyType: Option[String] = None
  ): EnrichedTelemetryEvent = {
    EnrichedTelemetryEvent(
      reading_id = UUID.randomUUID().toString,
      vehicle_id = s"VHC-${scala.util.Random.nextInt(1000)}",
      fleet_id = scala.util.Random.nextInt(5) + 1,
      speed_kmh = scala.util.Random.nextDouble() * 120,
      fuel_level_percent = scala.util.Random.nextDouble() * 100,
      engine_temp_c = 70 + scala.util.Random.nextDouble() * 50,
      gps_lat = 37.0 + scala.util.Random.nextDouble(),
      gps_long = -122.0 + scala.util.Random.nextDouble(),
      device_status = if (scala.util.Random.nextBoolean()) "OK" else "WARNING",
      timestamp = System.currentTimeMillis(),
      ingestion_timestamp = System.currentTimeMillis(),
      is_anomaly = isAnomaly,
      anomaly_type = anomalyType
    )
  }
}