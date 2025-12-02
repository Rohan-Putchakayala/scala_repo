package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import scala.util.Random

object ex9_student_scores {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("ex9_student_scores")
      .config("spark.ui.port", "4040")
      .master("local[*]")
      .getOrCreate()
    import spark.implicits._

    val numStudents = 100000
    val subjects = Array("Math", "Physics", "Chemistry", "Biology", "English")

    val scoresRDD = spark.sparkContext.parallelize(1 to numStudents, 20).map { id =>
      val name = "Student_" + Random.alphanumeric.take(5).mkString
      val scores = subjects.map(_ => Random.nextInt(101))
      (id.toLong, name, scores(0), scores(1), scores(2), scores(3), scores(4))
    }

    val scoresDF = scoresRDD.toDF("studentId", "name", "Math", "Physics", "Chemistry", "Biology", "English")

    // Average per student
    val dfAvg = scoresDF.withColumn("avgScore",
      (scoresDF("Math") + scoresDF("Physics") + scoresDF("Chemistry") + scoresDF("Biology") + scoresDF("English")) / 5
    )

    // Write output
    dfAvg.write.mode(SaveMode.Overwrite).parquet("output/ex9/parquet")
    dfAvg.write.mode(SaveMode.Overwrite).json("output/ex9/json")
    Thread.sleep(5000000)

    spark.stop()
  }
}
