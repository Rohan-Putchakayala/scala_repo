package services

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import play.api.Configuration
import play.api.libs.json.{Json, Writes}
import models.Models._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

@Singleton
class KafkaProducerService @Inject()(config: Configuration)(implicit ec: ExecutionContext) {
  
  private val kafkaBootstrapServers: String = config.getOptional[String]("kafka.bootstrap.servers").getOrElse("localhost:9092")
  private val kafkaEnabled: Boolean = config.getOptional[Boolean]("kafka.enabled").getOrElse(true)
  
  private val producerConfig = Map[String, Object](
    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaBootstrapServers,
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> classOf[StringSerializer].getName,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> classOf[StringSerializer].getName,
    ProducerConfig.ACKS_CONFIG -> "all",
    ProducerConfig.RETRIES_CONFIG -> "3",
    ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION -> "1",
    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG -> "true"
  ).asJava

  private val producer: Option[KafkaProducer[String, String]] =
    if (kafkaEnabled) Some(new KafkaProducer[String, String](producerConfig)) else None

  def sendMessage[T](topic: String, key: String, message: T)(implicit writes: Writes[T]): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val jsonMessage = Json.toJson(message).toString()
    val record = new ProducerRecord[String, String](topic, key, jsonMessage)

    producer match {
      case Some(p) =>
        p.send(record, (metadata: RecordMetadata, exception: Exception) => {
          if (exception != null) {
            promise.failure(exception)
          } else {
            promise.success(metadata)
          }
        })
      case None =>
        promise.success(null.asInstanceOf[RecordMetadata])
    }

    promise.future
  }

  
  def sendRoomPreparationNotification(topic: String, notification: models.RoomPreparationNotification): Future[RecordMetadata] = {
    sendMessage(topic, s"room-prep-${notification.reservationId}", notification)
  }

  def sendReservationReminder(topic: String, notification: models.ReservationReminderNotification): Future[RecordMetadata] = {
    sendMessage(topic, s"reminder-${notification.reservationId}", notification)
  }

  def sendRoomReleaseNotification(topic: String, notification: models.RoomReleaseNotification): Future[RecordMetadata] = {
    sendMessage(topic, s"release-${notification.reservationId}", notification)
  }

  def sendAdminNotification(topic: String, notification: models.AdminNotification): Future[RecordMetadata] = {
    sendMessage(topic, s"admin-${notification.reservationId}", notification)
  }

  def close(): Unit = {
    producer.foreach(_.close())
  }
}
