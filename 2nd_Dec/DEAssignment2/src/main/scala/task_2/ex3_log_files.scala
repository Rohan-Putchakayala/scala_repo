package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random

object ex3_log_files {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("ex3_log_files")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val levels = Array("INFO", "WARN", "ERROR")
    val numLogs = 5000000

    val logsRDD = spark.sparkContext
      .parallelize(1 to numLogs, 40)
      .map { _ =>
        val ts = System.currentTimeMillis() - Random.nextInt(10000000)
        val level = levels(Random.nextInt(levels.length))
        val msg = Random.alphanumeric.take(15).mkString
        val user = Random.nextInt(10000)
        s"$ts|$level|$msg|$user"
      }

    val logsDF = logsRDD
      .map(_.split("\\|"))
      .map(a => (a(0), a(1), a(2), a(3)))
      .toDF("timestamp", "level", "message", "userId")

    // RDD ERROR count
    val rddErrorCount = logsRDD.filter(_.contains("ERROR")).count()

    // DF ERROR count
    val dfErrorCount = logsDF.filter($"level" === "ERROR").count()

    // Write ERROR logs to text
    logsRDD.filter(_.contains("ERROR"))
      .saveAsTextFile("output/ex3/error_logs")

    // Full log DF to JSON
    logsDF.write.mode(SaveMode.Overwrite).json("output/ex3/json")
    Thread.sleep(5000000)

    spark.stop()
  }
}
