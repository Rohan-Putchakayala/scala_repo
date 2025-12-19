package com.smartfleet.common.database

import com.smartfleet.common.models._
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.{DynamoDbClient}
import software.amazon.awssdk.services.dynamodb.model._

import scala.util.{Try}
import scala.jdk.CollectionConverters._

import com.typesafe.config.Config

class DynamoDatabase(config: Config) {

  private val tableName = config.getString("dynamodb.table-name")
  private val region = Region.of(config.getString("dynamodb.region"))

  private val dynamo = {
    val b = DynamoDbClient.builder().region(region)

    val creds = AwsBasicCredentials.create(
      config.getString("dynamodb.access-key"),
      config.getString("dynamodb.secret-key")
    )
    b.credentialsProvider(StaticCredentialsProvider.create(creds))

    b.build()
  }

  def initializeTable(): Unit = {
    try {
      dynamo.describeTable(
        DescribeTableRequest.builder().tableName(tableName).build()
      )
      println(s"DynamoDB table already exists: $tableName")
    } catch {
      case _: ResourceNotFoundException =>
        val req = CreateTableRequest.builder()
          .tableName(tableName)
          .keySchema(
            KeySchemaElement.builder().attributeName("fleet_id").keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName("timestamp").keyType(KeyType.RANGE).build()
          )
          .attributeDefinitions(
            AttributeDefinition.builder().attributeName("fleet_id").attributeType(ScalarAttributeType.N).build(),
            AttributeDefinition.builder().attributeName("timestamp").attributeType(ScalarAttributeType.N).build()
          )
          .billingMode(BillingMode.PAY_PER_REQUEST)
          .build()

        dynamo.createTable(req)
        println(s"Created DynamoDB table: $tableName")
    }
  }

  def batchInsertTelemetryRecords(records: List[DynamoTelemetryRecord]): Unit = {
    val groups = records.grouped(25)

    groups.foreach { batch =>
      val writeReqs = batch.map { r =>

        // --- FIX HERE: Make timestamp unique ---
        val uniqueTs = r.timestamp * 1000 + scala.util.Random.nextInt(1000)

        WriteRequest.builder()
          .putRequest(
            PutRequest.builder().item(Map(
              "fleet_id" -> AttributeValue.builder().n(r.fleet_id.toString).build(),
              "timestamp" -> AttributeValue.builder().n(uniqueTs.toString).build(),
              "vehicle_id" -> AttributeValue.builder().s(r.vehicle_id).build(),
              "speed_kmh" -> AttributeValue.builder().n(r.speed_kmh.toString).build(),
              "fuel_level_percent" -> AttributeValue.builder().n(r.fuel_level_percent.toString).build(),
              "engine_temp_c" -> AttributeValue.builder().n(r.engine_temp_c.toString).build(),
              "gps_lat" -> AttributeValue.builder().n(r.gps_lat.toString).build(),
              "gps_long" -> AttributeValue.builder().n(r.gps_long.toString).build(),
              "device_status" -> AttributeValue.builder().s(r.device_status).build()
            ).asJava).build()
          )
          .build()
      }.asJava

      val req = BatchWriteItemRequest.builder()
        .requestItems(Map(tableName -> writeReqs).asJava)
        .build()

      dynamo.batchWriteItem(req)
    }
  }


  def retainLatestPerFleet(fleet: Int, keep: Int): Try[Int] = Try {
    val query = QueryRequest.builder()
      .tableName(tableName)
      .keyConditionExpression("fleet_id = :fid")
      .expressionAttributeValues(Map(
        ":fid" -> AttributeValue.builder().n(fleet.toString).build()
      ).asJava)
      .scanIndexForward(false)
      .build()

    val items = dynamo.query(query).items().asScala.toList
    if (items.size <= keep) 0

    val toDelete = items.drop(keep)

    toDelete.foreach { item =>
      val delReq = DeleteItemRequest.builder()
        .tableName(tableName)
        .key(Map(
          "fleet_id" -> item.get("fleet_id"),
          "timestamp" -> item.get("timestamp")
        ).asJava)
        .build()

      dynamo.deleteItem(delReq)
    }

    toDelete.size
  }

  /**
   * Get recent readings for a fleet (last N readings)
   */
  def getRecentReadings(fleetId: Int, limit: Int): Try[List[DynamoTelemetryRecord]] = Try {
    val query = QueryRequest.builder()
      .tableName(tableName)
      .keyConditionExpression("fleet_id = :fid")
      .expressionAttributeValues(Map(
        ":fid" -> AttributeValue.builder().n(fleetId.toString).build()
      ).asJava)
      .scanIndexForward(false) // Latest first
      .limit(limit)
      .build()

    val items = dynamo.query(query).items().asScala.toList
    
    items.map { item =>
      DynamoTelemetryRecord(
        fleet_id = item.get("fleet_id").n().toInt,
        timestamp = item.get("timestamp").n().toLong,
        vehicle_id = item.get("vehicle_id").s(),
        speed_kmh = item.get("speed_kmh").n().toDouble,
        fuel_level_percent = item.get("fuel_level_percent").n().toDouble,
        engine_temp_c = item.get("engine_temp_c").n().toDouble,
        gps_lat = item.get("gps_lat").n().toDouble,
        gps_long = item.get("gps_long").n().toDouble,
        device_status = item.get("device_status").s()
      )
    }
  }

  /**
   * Get recent readings for a specific vehicle
   */
  def getRecentReadingsForVehicle(fleetId: Int, vehicleId: String, limit: Int): Try[List[DynamoTelemetryRecord]] = Try {
    val query = QueryRequest.builder()
      .tableName(tableName)
      .keyConditionExpression("fleet_id = :fid")
      .filterExpression("vehicle_id = :vid")
      .expressionAttributeValues(Map(
        ":fid" -> AttributeValue.builder().n(fleetId.toString).build(),
        ":vid" -> AttributeValue.builder().s(vehicleId).build()
      ).asJava)
      .scanIndexForward(false) // Latest first
      .limit(limit * 3) // Get more records to account for filtering
      .build()

    val items = dynamo.query(query).items().asScala.toList
    
    val records = items.map { item =>
      DynamoTelemetryRecord(
        fleet_id = item.get("fleet_id").n().toInt,
        timestamp = item.get("timestamp").n().toLong,
        vehicle_id = item.get("vehicle_id").s(),
        speed_kmh = item.get("speed_kmh").n().toDouble,
        fuel_level_percent = item.get("fuel_level_percent").n().toDouble,
        engine_temp_c = item.get("engine_temp_c").n().toDouble,
        gps_lat = item.get("gps_lat").n().toDouble,
        gps_long = item.get("gps_long").n().toDouble,
        device_status = item.get("device_status").s()
      )
    }
    
    // Filter by vehicle_id and take only the requested limit
    records.filter(_.vehicle_id == vehicleId).take(limit)
  }
}
