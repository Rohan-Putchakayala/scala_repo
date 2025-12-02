package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random

object ex8_employee_data {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("ex8_employee_data")
      .config("spark.ui.port", "4040")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val departments = Array("HR", "ENG", "FIN", "OPS")
    val numEmployees = 1000000

    val empRDD = spark.sparkContext
      .parallelize(1 to numEmployees, 30)
      .map { id =>
        val name = Random.alphanumeric.take(8).mkString
        val dept = departments(Random.nextInt(departments.length))
        val salary = 30000 + Random.nextInt(120000)
        (id, name, dept, salary)
      }

    val empDF = empRDD.toDF("empId", "name", "dept", "salary")

    empDF.groupBy("dept").avg("salary")
      .write.mode(SaveMode.Overwrite).csv("output/ex8/csv")
    Thread.sleep(5000000)

    spark.stop()
  }
}
