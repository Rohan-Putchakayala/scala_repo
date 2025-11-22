package services

import akka.actor.ActorRef
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json.Json
import org.slf4j.LoggerFactory
import models._
import scala.concurrent.duration._
import java.time.Duration
import java.util.Properties
import scala.collection.JavaConverters._

class KafkaConsumerService(system: akka.actor.ActorSystem,
                           itSupportActor: ActorRef,
                           securityActor: ActorRef) {

  private val props = new Properties()
  props.put("bootstrap.servers", "localhost:9092")
  props.put("group.id", "visitor-management")
  props.put("key.deserializer", classOf[StringDeserializer].getName)
  props.put("value.deserializer", classOf[StringDeserializer].getName)
  props.put("auto.offset.reset", "earliest")
  props.put("enable.auto.commit", "true")

  private val consumer = new KafkaConsumer[String, String](props)
  private val topics = List("visitor-it-notifications", "visitor-security-notifications", "visitor-checkout-notifications")

  def start(): Unit = {
    import system.dispatcher
    import models.NotificationJsonFormats._

    val logger = LoggerFactory.getLogger(classOf[KafkaConsumerService])

    consumer.subscribe(topics.asJava)

    system.scheduler.scheduleAtFixedRate(1.second, 1.second) { () =>
      val records = consumer.poll(Duration.ofMillis(100))
      records.asScala.foreach { record =>
        record.topic() match {
          case "visitor-it-notifications" =>
            val notification = Json.parse(record.value()).as[ITSupportNotification]
            itSupportActor ! notification
          case "visitor-security-notifications" =>
            val notification = Json.parse(record.value()).as[SecurityNotification]
            securityActor ! notification
          case "visitor-checkout-notifications" =>
            val notification = Json.parse(record.value()).as[CheckOutNotification]
            logger.info(s"Received check-out notification: $notification")
        }
      }
    }
  }

  def stop(): Unit = {
    consumer.close()
  }
}