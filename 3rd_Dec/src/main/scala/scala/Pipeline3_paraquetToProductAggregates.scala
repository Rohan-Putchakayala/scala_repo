package pipeline3

import com.typesafe.config.ConfigFactory
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.{SparkSession}
import org.apache.spark.sql.functions._

object Pipeline3_paraquetToProductAggregates {

  def main(args: Array[String]): Unit = {

    // ------------------------------------------------------
    // Load configuration
    // ------------------------------------------------------
    val baseConf   = ConfigFactory.load()
    val appConfig  = baseConf.getConfig("app")
    val s3Settings = baseConf.getConfig("keyspaces")

    val parquetSource = appConfig.getString("parquetPath")
    val outputRoot    = appConfig.getString("aggregatesPath")
    val outputName    = "products.json"

    val accessKey = s3Settings.getString("accesskey")
    val secretKey = s3Settings.getString("secretkey")
    val region    = if (s3Settings.hasPath("region")) s3Settings.getString("region") else "us-east-1"
    val endpoint  = if (s3Settings.hasPath("endpoint")) s3Settings.getString("endpoint") else ""

    // ------------------------------------------------------
    // Build Spark session with S3A settings
    // ------------------------------------------------------
    val sessionBuilder =
      SparkSession.builder()
        .appName("Product Aggregation -> JSON Exporter")
        .master("local[*]")
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")
        .config("spark.hadoop.fs.s3a.access.key", accessKey)
        .config("spark.hadoop.fs.s3a.secret.key", secretKey)
        .config("spark.hadoop.fs.s3a.region", region)

    if (endpoint.nonEmpty)
      sessionBuilder.config("spark.hadoop.fs.s3a.endpoint", endpoint)

    val spark = sessionBuilder.getOrCreate()
    import spark.implicits._

    // mirror settings into Hadoop config explicitly
    val hConf = spark.sparkContext.hadoopConfiguration
    hConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
    hConf.set("fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")
    hConf.set("fs.s3a.access.key", accessKey)
    hConf.set("fs.s3a.secret.key", secretKey)
    hConf.set("fs.s3a.fast.upload", "true")
    hConf.set("fs.s3a.connection.maximum", "100")

    if (endpoint.nonEmpty)
      hConf.set("fs.s3a.endpoint", endpoint)
    else
      hConf.set("fs.s3a.region", region)

    try {

      // ------------------------------------------------------
      // 1. Read Parquet Data
      // ------------------------------------------------------
      val df = spark.read.parquet(parquetSource)

      // ------------------------------------------------------
      // 2. Compute Product Aggregations
      // ------------------------------------------------------
      val computedAgg =
        df.filter($"product_name".isNotNull)
          .groupBy($"product_name")
          .agg(
            sum($"quantity".cast("long")).as("total_quantity"),
            round(sum($"amount".cast("double")), 2).as("total_revenue")
          )
          .orderBy(desc("total_revenue"))

      println("=== Highest Revenue Products ===")
      computedAgg.show(20, truncate = false)

      // ------------------------------------------------------
      // 3. Write Single JSON File (coalesce & rename)
      // ------------------------------------------------------
      val tempDir        = s"${outputRoot.stripSuffix("/")}/_tmp_json_${System.currentTimeMillis()}/"
      val finalDir       = s"${outputRoot.stripSuffix("/")}/"
      val finalJsonPath  = finalDir + outputName

      computedAgg
        .coalesce(1)
        .write
        .mode("overwrite")
        .json(tempDir)

      // FS instance for renaming
      val fs = FileSystem.get(new java.net.URI(finalDir), hConf)
      val tmpFolder = new Path(tempDir)

      // Identify the generated Spark JSON file
      val jsonParts = fs.listStatus(tmpFolder)
        .map(_.getPath)
        .filter(p => p.getName.startsWith("part-") && p.getName.endsWith(".json"))

      if (jsonParts.isEmpty) {
        throw new RuntimeException(s"No JSON part file detected under $tempDir")
      }

      val srcFile = jsonParts.head
      val dstFile = new Path(finalJsonPath)

      if (fs.exists(dstFile)) fs.delete(dstFile, false)

      val renameOk = fs.rename(srcFile, dstFile)
      if (!renameOk)
        throw new RuntimeException(s"Renaming failed: ${srcFile.toString} -> ${dstFile.toString}")

      println(s"JSON export ready at: $finalJsonPath")

      // cleanup temp folder
      fs.delete(tmpFolder, true)
      println("Temporary artifacts cleaned up.")

    } catch {
      case err: Throwable =>
        System.err.println(s"Pipeline error: ${err.getMessage}")
        err.printStackTrace()
    } finally {
      spark.stop()
    }
  }
}
