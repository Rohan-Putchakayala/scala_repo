package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random
import java.time._

object ex5_iot_sensor {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("ex5_iot_sensor")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val numSensors = 100
    val numReadings = 5000000

    val sensors = (1 to numSensors).map(i => "Sensor_" + i).toArray

    val sensorRDD = spark.sparkContext
      .parallelize(1 to numReadings, 50)
      .map { _ =>
        val sensorId = sensors(Random.nextInt(sensors.length))
        val ts = LocalDateTime.now().minusSeconds(Random.nextInt(1000000))
        val temp = (Random.nextDouble() * 40).formatted("%.2f").toDouble
        val humidity = (Random.nextDouble() * 100).formatted("%.2f").toDouble
        (sensorId, ts.toString, temp, humidity)
      }

    val sensorDF = sensorRDD.toDF("sensorId", "timestamp", "temperature", "humidity")

    // Aggregations
    val avgReadings = sensorDF.groupBy("sensorId")
      .agg(
        org.apache.spark.sql.functions.avg("temperature").alias("avgTemp"),
        org.apache.spark.sql.functions.avg("humidity").alias("avgHumidity")
      )

    // Write outputs
    avgReadings.write.mode(SaveMode.Overwrite).parquet("output/ex5/parquet")
    avgReadings.write.mode(SaveMode.Overwrite).json("output/ex5/json")
    Thread.sleep(5000000)

    spark.stop()
  }
}
