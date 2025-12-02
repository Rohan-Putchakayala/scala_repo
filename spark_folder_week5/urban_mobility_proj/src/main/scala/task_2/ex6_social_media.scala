package task_2

import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import scala.util.Random

object ex6_social_media {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder()
      .appName("ex6_social_media")
      .config("spark.ui.port", "4040")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    // --------------------------
    // 1. Generate Users (1M)
    // --------------------------
    val userCount = 1000000   // Scala 2.12: no underscore
    val userRDD = spark.sparkContext.parallelize(1 to userCount, 30).map { id =>
      val name = Random.alphanumeric.take(8).mkString
      val age = 15 + Random.nextInt(60)  // 15–74
      (id, name, age)
    }

    val userDF = userRDD.toDF("userId", "name", "age")

    // --------------------------
    // 2. Generate Posts (2M)
    // --------------------------
    val postCount = 2000000
    val postRDD = spark.sparkContext.parallelize(1 to postCount, 40).map { pid =>
      val user = Random.nextInt(userCount) + 1
      val text = Random.alphanumeric.take(20).mkString
      (pid, user, text)
    }

    val postDF = postRDD.toDF("postId", "userId", "text")

    // --------------------------
    // 3. Join Users and Posts
    // --------------------------
    val joinedDF = postDF.join(userDF, "userId")  // join on userId

    // --------------------------
    // 4. Aggregate: Posts per Age Group
    // --------------------------
    val postsPerAgeGroup = joinedDF.groupBy("age").count()
      .withColumnRenamed("count", "postCount")
      .orderBy("age")

    // --------------------------
    // 5. Write Output
    // --------------------------
    postsPerAgeGroup.write.mode(SaveMode.Overwrite).json("output/ex6/posts_per_age_group.json")

    println("hjvjhdgvhkdagkjdljvsjlvbjksdlbvkj*****************************")
    Thread.sleep(5000000)
    spark.stop()
  }
}
