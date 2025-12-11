package scala

import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._

object Pipeline1_WriteToKeyspaces {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Pipeline1_RDS_to_Keyspaces")
      .master("local[*]")
      .config("spark.cassandra.connection.host", "DUMMY")
      .config("spark.cassandra.connection.port", "9142")
      .config("spark.cassandra.connection.ssl.enabled", "true")
      // Use secrets managers / IAM in prod; placeholders here:
      .config("spark.cassandra.auth.username", "DUMMY")
      .config("spark.cassandra.auth.password", "DUMMY")
      .getOrCreate()

    import spark.implicits._

    val jdbcUrl = "jdbc:mysql://DUMMY"
    val jdbcProps = new java.util.Properties()
    jdbcProps.setProperty("user", "DUMMY")
    jdbcProps.setProperty("password", "DUMMY")
    jdbcProps.setProperty("driver", "com.mysql.cj.jdbc.Driver")

    val customersDF = spark.read.jdbc(jdbcUrl, "customers", jdbcProps)
    val ordersDF = spark.read.jdbc(jdbcUrl, "orders", jdbcProps)
    val itemsDF = spark.read.jdbc(jdbcUrl, "order_items", jdbcProps)

    val joined = customersDF.alias("c")
      .join(ordersDF.alias("o"), $"c.customer_id" === $"o.customer_id")
      .join(itemsDF.alias("i"), $"o.order_id" === $"i.order_id")
      .select(
        $"c.customer_id",
        $"c.name",
        $"c.email",
        $"c.city",
        $"o.order_id",
        $"o.order_date",
        $"o.amount",
        $"i.item_id",
        $"i.product_name",
        $"i.quantity"
      )

    val finalDF = joined.withColumn("order_date", $"order_date".cast("timestamp"))

    println(finalDF)

    finalDF.write
      .format("org.apache.spark.sql.cassandra")
      .option("keyspace", "retail")
      .option("table", "sales_data")
      .mode(SaveMode.Append)
      .save()

    spark.stop()
  }
}
