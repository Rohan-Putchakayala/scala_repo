package services

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import play.api.Configuration
import play.api.libs.json.{Json, Writes}
import models._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

@Singleton
class KafkaProducerService @Inject()(config: Configuration)(implicit ec: ExecutionContext) {
  
  private val kafkaBootstrapServers: String = config.getOptional[String]("kafka.bootstrap.servers").getOrElse("localhost:9092")
  
  private val producerConfig = Map[String, Object](
    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaBootstrapServers,
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> classOf[StringSerializer].getName,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> classOf[StringSerializer].getName,
    ProducerConfig.ACKS_CONFIG -> "all",
    ProducerConfig.RETRIES_CONFIG -> "3",
    ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION -> "1",
    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG -> "true"
  ).asJava

  private val producer = new KafkaProducer[String, String](producerConfig)

  def sendMessage[T](topic: String, key: String, message: T)(implicit writes: Writes[T]): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val jsonMessage = Json.toJson(message).toString()
    val record = new ProducerRecord[String, String](topic, key, jsonMessage)

    producer.send(record, (metadata: RecordMetadata, exception: Exception) => {
      if (exception != null) {
        promise.failure(exception)
      } else {
        promise.success(metadata)
      }
    })

    promise.future
  }

  // Host notifications are handled directly via EmailService, so no Kafka producer
  // is required for hosts at the moment.

  def sendITSupportNotification(topic: String, notification: models.ITSupportNotification): Future[RecordMetadata] = {
    sendMessage(topic, s"it-${notification.visitorId}", notification)
  }

  def sendSecurityNotification(topic: String, notification: models.SecurityNotification): Future[RecordMetadata] = {
    sendMessage(topic, s"security-${notification.visitorId}", notification)
  }

  def sendCheckOutNotification(topic: String, notification: models.CheckOutNotification): Future[RecordMetadata] = {
    // Uses implicit checkOutNotificationFormat from models.Models
    sendMessage(topic, s"checkout-${notification.checkInId}", notification)
  }

  def close(): Unit = {
    producer.close()
  }
}
