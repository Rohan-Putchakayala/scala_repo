# Explanation of Partitioning Logic

## Overview

All three Smart Fleet pipelines use partitioning to balance parallelism, reduce network shuffle, and make downstream storage/querying efficient. Partitioning happens at three places: (1) how Kafka streams are partitioned and consumed, (2) how Spark repartitions data for groupBy/joins and downstream writes, and (3) how final data is partitioned in storage (S3 hive-style date/hour partitions and MySQL fleet-based partitioning).

## Pipeline2 — Kafka → Spark Streaming → DynamoDB + S3 (Real-time telemetry processing)

### Partitioning steps & heuristics

#### 1. Kafka partitioning (Producer behavior)
- **Vehicle Simulator** produces telemetry events to Kafka topic `vehicle_telemetry_topic` with 3 partitions
- **Partitioning key**: Events are distributed across partitions using Kafka's default partitioner (round-robin or hash-based)
- **Purpose**: Enables parallel consumption by Spark Structured Streaming across multiple Kafka partitions

#### 2. Spark streaming partitioning & processing
- **Kafka consumption**: Spark reads from all 3 Kafka partitions in parallel using `spark.readStream.format("kafka")`
- **Micro-batch processing**: Each micro-batch processes events from all partitions together
- **No explicit repartitioning**: Events are processed as they arrive, maintaining Kafka partition locality
- **Deduplication strategy**: Groups events by `(vehicle_id, timestamp)` to handle duplicates before DynamoDB writes

#### 3. DynamoDB partitioning (storage layout)
- **Partition key**: `fleet_id` (enables efficient queries by fleet)
- **Sort key**: `timestamp` (enables time-range queries)
- **Write strategy**: Batch writes are grouped by fleet_id to optimize DynamoDB partition utilization

#### 4. S3 partitioning (protobuf files)
- **Hive-style partitioning**: `telemetry/enriched/date=YYYY-MM-DD/hour=HH/`
- **File size management**: Events are split into ~80KB protobuf files using `splitFiles()` method:
  ```scala
  val MAX_FILE_BYTES = 80 * 1024         // hard limit
  val EVENT_EST_BYTES = 180              // approx per-event protobuf size
  ```
- **Temporal partitioning**: Uses event timestamp to determine date/hour partition
- **File naming**: `part-{batchId}-{partIndex}.pb` for unique identification

## Pipeline3 — S3 Protobuf → Spark Batch → MySQL (Daily fleet summaries)

### Partitioning steps & heuristics

#### 1. S3 read partitioning
- **Date-based filtering**: Reads all protobuf files for a specific date: `telemetry/enriched/date=$targetDate/`
- **Parallel file reading**: Each protobuf file is read in parallel using Scala collections
- **Memory management**: Processes files sequentially to avoid memory issues with large datasets

#### 2. Spark DataFrame partitioning & aggregation
- **Data loading**: Creates Spark DataFrame from List of events: `spark.createDataFrame(protobufData)`
- **Join strategy**: 
  ```scala
  val joined = telemetryRen.join(vehicleRen, Seq("vehicle_id"), "left")
  ```
- **Aggregation partitioning**: Groups by `fleet_id` for summary calculations:
  ```scala
  val fleetAggregates = telemetryWithDistance.groupBy("fleet_id").agg(
    sum("distance_km").alias("total_distance_km"),
    avg("speed_kmh").alias("avg_speed"),
    // ... other aggregations
  )
  ```

#### 3. MySQL partitioning (storage layout)
- **Fleet-based organization**: Each fleet summary is a separate row with `fleet_id` as primary identifier
- **Date partitioning**: Records are partitioned by `record_date` for efficient time-based queries
- **Write strategy**: Individual inserts per fleet to handle conflicts gracefully

## Pipeline4 — S3 Protobuf → Spark Batch → S3 Reports (Dashboard analytics)

### Partitioning steps & heuristics

#### 1. S3 read partitioning (same as Pipeline3)
- **Date-based filtering**: Uses `ProtobufConverter.readProtobufDataForDate()` to read all files for target date
- **Parallel processing**: Processes multiple protobuf files concurrently
- **Error handling**: Continues processing even if individual files fail to read

#### 2. Spark DataFrame partitioning & report generation
- **Multi-table joins**: Joins telemetry with fleet and vehicle metadata:
  ```scala
  val joined = telemetryAliased
    .join(vehicleAliased, col("t.vehicle_id") === col("v.vehicle_id"))
    .join(fleetAliased, col("t.fleet_id") === col("f.fleet_id"))
  ```
- **Fleet-based aggregation**: All three reports group by `(fleet_id, fleet_name)`:
  - **Speed Report**: `avg("speed_kmh")`, `max("speed_kmh")`, `min("speed_kmh")`
  - **Fuel Report**: `sum("fuel_consumed")`, `avg("fuel_level_percent")`
  - **Anomaly Report**: `sum(when(col("is_anomaly"), 1))` with anomaly type breakdowns

#### 3. S3 report partitioning (output)
- **Report-type partitioning**: Each report type gets its own S3 path:
  - `reports/daily_avg_speed_by_fleet/date=$date/report.pb`
  - `reports/daily_fuel_consumption_by_fleet/date=$date/report.pb`
  - `reports/daily_anomaly_counts/date=$date/report.pb`
- **Date partitioning**: Reports are partitioned by date for efficient historical analysis
- **Protobuf serialization**: Uses custom protobuf encoding for compact storage and fast deserialization

## Partitioning Performance Optimizations

### File Size Management
- **Pipeline2**: Splits large batches into ~80KB files to optimize S3 performance and downstream processing
- **Pipeline3/4**: Reads multiple small files in parallel to maximize throughput

### Memory Management
- **Streaming (Pipeline2)**: Processes micro-batches to maintain constant memory usage
- **Batch (Pipeline3/4)**: Loads entire datasets into memory for complex aggregations, with safeguards for large datasets

### Network Optimization
- **S3 reads**: Parallel file reading reduces total read time
- **S3 writes**: Batch uploads with proper content-type headers (`application/x-protobuf`)
- **DynamoDB**: Batch writes grouped by partition key to minimize cross-partition operations

### Query Optimization
- **Time-based queries**: Date/hour partitioning enables efficient time-range filtering
- **Fleet-based queries**: Fleet_id partitioning enables fast fleet-specific analytics
- **Report queries**: Separate report types enable targeted dashboard queries without scanning irrelevant data

## Monitoring and Debugging
- **Spark UI availability**: Each pipeline keeps Spark UI alive for debugging:
  - Pipeline2: http://localhost:4041/jobs/ (streaming)
  - Pipeline3: http://localhost:4043/jobs/ (batch summaries)
  - Pipeline4: http://localhost:4040/jobs/ (batch reports)
- **Checkpoint management**: Pipeline2 uses unique checkpoint locations to avoid conflicts
- **Error handling**: All pipelines include comprehensive error handling with detailed logging