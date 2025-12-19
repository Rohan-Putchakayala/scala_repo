package com.smartfleet.common.utils

import com.smartfleet.common.models.EnrichedTelemetryEvent
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.collection.mutable.ListBuffer

/**
 * S3 Protobuf Writer with size-based batching and partitioning
 * Writes telemetry events to S3 in protobuf format with strict size limits
 */
class S3ProtobufWriter(
  bucket: String,
  basePath: String,
  accessKey: String,
  secretKey: String,
  region: String
) extends Serializable {

  // Size constraints (in bytes)
  private val MIN_FILE_SIZE = 50 * 1024  // 50 KB
  private val MAX_FILE_SIZE = 80 * 1024  // 80 KB
  private val TARGET_FILE_SIZE = 60 * 1024  // 60 KB (target)

  // S3 Client (lazy initialization for Spark serialization)
  @transient private lazy val s3Client: S3Client = {
    val credentials = AwsBasicCredentials.create(accessKey, secretKey)
    S3Client.builder()
      .region(Region.of(region))
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .build()
  }

  /**
   * Estimate protobuf size for a single event (approximate)
   */
  def estimateEventSize(event: EnrichedTelemetryEvent): Int = {
    // Rough estimation based on field sizes
    val stringFields = event.reading_id.length + event.vehicle_id.length + event.device_status.length
    val anomalyTypeSize = event.anomaly_type.map(_.length).getOrElse(0)
    val fixedFields = 8 + 4 + 8 + 8 + 8 + 8 + 8 + 8 + 1 + 8 + 8 // numeric fields + boolean
    val protobufOverhead = 20 // tags, length prefixes, etc.
    
    stringFields + anomalyTypeSize + fixedFields + protobufOverhead
  }

  /**
   * Batch events by size constraints
   */
  def createSizeBatches(events: List[EnrichedTelemetryEvent]): List[List[EnrichedTelemetryEvent]] = {
    val batches = ListBuffer[List[EnrichedTelemetryEvent]]()
    val currentBatch = ListBuffer[EnrichedTelemetryEvent]()
    var currentSize = 0
    
    // Estimate batch header size (batch_id, timestamp, counts)
    val batchHeaderSize = 100
    currentSize += batchHeaderSize

    for (event <- events) {
      val eventSize = estimateEventSize(event)
      
      // Check if adding this event would exceed MAX_FILE_SIZE
      if (currentSize + eventSize > MAX_FILE_SIZE && currentBatch.nonEmpty) {
        // Finalize current batch
        batches += currentBatch.toList
        currentBatch.clear()
        currentSize = batchHeaderSize
      }
      
      // Add event to current batch
      currentBatch += event
      currentSize += eventSize
    }
    
    // Add final batch if not empty
    if (currentBatch.nonEmpty) {
      batches += currentBatch.toList
    }
    
    batches.toList
  }

  /**
   * Write a batch of events to S3 as protobuf
   */
  def writeBatchToS3(
    events: List[EnrichedTelemetryEvent],
    date: String,
    hour: String,
    partNumber: Int
  ): S3WriteResult = {
    try {
      // Generate batch ID
      val batchId = s"batch_${date}_${hour}_${partNumber}_${UUID.randomUUID().toString.take(8)}"
      
      // Convert to protobuf bytes
      val protobufBytes = ProtobufConverter.toBatchProtobufBytes(events, batchId)
      
      // Validate size constraints
      if (protobufBytes.length > MAX_FILE_SIZE) {
        throw new IllegalStateException(s"Protobuf batch size ${protobufBytes.length} exceeds maximum ${MAX_FILE_SIZE}")
      }
      
      // Create S3 key with partitioning
      val s3Key = s"$basePath/date=$date/hour=$hour/part-${String.format("%05d", partNumber)}.pb"
      
      // Upload to S3
      val putRequest = PutObjectRequest.builder()
        .bucket(bucket)
        .key(s3Key)
        .contentType("application/x-protobuf")
        .contentLength(protobufBytes.length.toLong)
        .build()
      
      s3Client.putObject(putRequest, RequestBody.fromBytes(protobufBytes))
      
      S3WriteResult(
        success = true,
        s3Key = s3Key,
        fileSizeBytes = protobufBytes.length,
        eventCount = events.size,
        batchId = batchId,
        error = None
      )
      
    } catch {
      case e: Exception =>
        S3WriteResult(
          success = false,
          s3Key = "",
          fileSizeBytes = 0,
          eventCount = events.size,
          batchId = "",
          error = Some(e.getMessage)
        )
    }
  }

  /**
   * Process events for a specific hour with size-based batching
   */
  def writeHourlyEvents(
    events: List[EnrichedTelemetryEvent],
    date: String,
    hour: String
  ): List[S3WriteResult] = {
    
    println(s"📦 Processing ${events.size} events for $date hour $hour")
    
    // Create size-based batches
    val batches = createSizeBatches(events)
    println(s"📊 Created ${batches.size} batches for size constraints")
    
    // Write each batch to S3
    val results = batches.zipWithIndex.map { case (batch, index) =>
      val partNumber = index + 1
      val estimatedSize = batch.map(estimateEventSize).sum
      
      println(s"   📝 Writing part $partNumber: ${batch.size} events, ~${estimatedSize/1024}KB")
      
      val result = writeBatchToS3(batch, date, hour, partNumber)
      
      if (result.success) {
        println(s"   ✅ Part $partNumber: ${result.fileSizeBytes/1024}KB written to ${result.s3Key}")
      } else {
        println(s"   ❌ Part $partNumber failed: ${result.error.getOrElse("Unknown error")}")
      }
      
      result
    }
    
    // Summary
    val successCount = results.count(_.success)
    val totalSize = results.filter(_.success).map(_.fileSizeBytes).sum
    println(s"📈 Hour $hour summary: $successCount/${results.size} files written, ${totalSize/1024}KB total")
    
    results
  }

  /**
   * Main method to process telemetry events with date/hour partitioning
   */
  def writePartitionedEvents(events: List[EnrichedTelemetryEvent]): PartitionedWriteResult = {
    println(s"🚀 Starting partitioned write for ${events.size} events")
    
    // Group events by date and hour
    val partitionedEvents = events.groupBy { event =>
      val instant = Instant.ofEpochMilli(event.timestamp)
      val zonedDateTime = instant.atZone(ZoneId.of("UTC"))
      val date = zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      val hour = String.format("%02d", zonedDateTime.getHour)
      (date, hour)
    }
    
    println(s"📅 Found ${partitionedEvents.size} date/hour partitions")
    
    // Process each partition
    val allResults = partitionedEvents.flatMap { case ((date, hour), hourEvents) =>
      writeHourlyEvents(hourEvents, date, hour)
    }.toList
    
    // Calculate summary statistics
    val successfulWrites = allResults.filter(_.success)
    val failedWrites = allResults.filterNot(_.success)
    val totalEvents = allResults.map(_.eventCount).sum
    val totalSize = successfulWrites.map(_.fileSizeBytes).sum
    val uniqueDates = partitionedEvents.keys.map(_._1).toSet.size
    val uniqueHours = partitionedEvents.keys.map(_._2).toSet.size
    
    println(s"🎯 Write Summary:")
    println(s"   ✅ Successful files: ${successfulWrites.size}")
    println(s"   ❌ Failed files: ${failedWrites.size}")
    println(s"   📊 Total events: $totalEvents")
    println(s"   💾 Total size: ${totalSize/1024}KB")
    println(s"   📅 Date partitions: $uniqueDates")
    println(s"   🕐 Hour partitions: ${partitionedEvents.size}")
    
    PartitionedWriteResult(
      totalFiles = allResults.size,
      successfulFiles = successfulWrites.size,
      failedFiles = failedWrites.size,
      totalEvents = totalEvents,
      totalSizeBytes = totalSize,
      partitions = partitionedEvents.size,
      results = allResults
    )
  }
}

/**
 * Result of writing a single batch to S3
 */
case class S3WriteResult(
  success: Boolean,
  s3Key: String,
  fileSizeBytes: Int,
  eventCount: Int,
  batchId: String,
  error: Option[String]
)

/**
 * Result of writing partitioned events
 */
case class PartitionedWriteResult(
  totalFiles: Int,
  successfulFiles: Int,
  failedFiles: Int,
  totalEvents: Int,
  totalSizeBytes: Long,
  partitions: Int,
  results: List[S3WriteResult]
)

/**
 * Companion object with utility methods
 */
object S3ProtobufWriter {
  
  /**
   * Create S3ProtobufWriter from configuration
   */
  def fromConfig(config: com.typesafe.config.Config): S3ProtobufWriter = {
    new S3ProtobufWriter(
      bucket = config.getString("s3.bucket-name"),
      basePath = config.getString("s3.paths.raw"),
      accessKey = config.getString("aws.access-key"),
      secretKey = config.getString("aws.secret-key"),
      region = config.getString("s3.region")
    )
  }
  
  /**
   * Validate file size constraints
   */
  def validateFileSize(sizeBytes: Int): (Boolean, String) = {
    val minSize = 50 * 1024
    val maxSize = 80 * 1024
    
    if (sizeBytes < minSize) {
      (false, s"File size ${sizeBytes/1024}KB is below minimum ${minSize/1024}KB")
    } else if (sizeBytes > maxSize) {
      (false, s"File size ${sizeBytes/1024}KB exceeds maximum ${maxSize/1024}KB")
    } else {
      (true, s"File size ${sizeBytes/1024}KB is within acceptable range")
    }
  }
}